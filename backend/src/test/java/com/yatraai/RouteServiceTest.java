package com.yatraai;

import com.yatraai.model.JourneyEvent;
import com.yatraai.model.JourneyLocationRequest;
import com.yatraai.model.JourneyLocationResponse;
import com.yatraai.model.JourneyState;
import com.yatraai.model.LocationPoint;
import com.yatraai.model.Place;
import com.yatraai.model.RouteInfo;
import com.yatraai.model.RoutePoint;
import com.yatraai.provider.routing.RoutingProvider;
import com.yatraai.service.GeocodingService;
import com.yatraai.service.JourneyService;
import com.yatraai.service.RouteService;
import com.yatraai.service.StoryTriggerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class RouteServiceTest {

    @Autowired
    private RouteService routeService;

    @Autowired
    private StoryTriggerService storyTriggerService;

    @Autowired
    private JourneyService journeyService;

    @Autowired
    private GeocodingService geocodingService;

    @BeforeEach
    void setUp() {
        storyTriggerService.resetJourney();
        storyTriggerService.setCooldownSeconds(300);
    }

    private RouteInfo createSampleNorthboundRoute() {
        // Simple straight northbound route from lat 17.3000 to 17.4000 along lon 78.4800 (~11.1 km)
        List<RoutePoint> geometry = List.of(
                new RoutePoint(17.3000, 78.4800),
                new RoutePoint(17.3500, 78.4800),
                new RoutePoint(17.4000, 78.4800)
        );
        double totalDist = routeService.calculateHaversineDistance(17.3000, 78.4800, 17.4000, 78.4800);
        return new RouteInfo("North City", 17.4000, 78.4800, totalDist, totalDist, 0.0, geometry);
    }

    @Test
    @DisplayName("Route filtering: places directly in route corridor vs far off corridor")
    void testRouteFiltering_PlacesOnRouteVsOffRoute() {
        RouteInfo route = createSampleNorthboundRoute();

        // User is at start (17.3000, 78.4800)
        double userLat = 17.3000;
        double userLon = 78.4800;

        // Place A: right along the route (~50m offset east, ~3.3km ahead)
        Place placeOnRoute = new Place("p1", "Fort on Route", "fort_palace", "Historic fort",
                17.3300, 78.4805, 3300.0, 10);

        // Place B: 15km far east of the route (not in corridor)
        Place placeOffRoute = new Place("p2", "Distant Palace", "fort_palace", "Far palace",
                17.3300, 78.6200, 15000.0, 10);

        RouteService.ProjectionResult projUser = routeService.projectPointOnPolyline(userLat, userLon, route.geometry());
        RouteService.ClassifiedRouteSummary summary = routeService.classifyPlacesAlongRoute(
                route,
                projUser.alongTrackDistanceMeters(),
                userLat,
                userLon,
                0.0, // Heading North
                15.0, // 15 m/s (~54 km/h)
                List.of(placeOnRoute, placeOffRoute)
        );

        // Place on route must be in upcoming places
        assertTrue(summary.upcomingPlaces().stream().anyMatch(p -> p.id().equals("p1")));
        // Place far off route must NOT be in upcoming places
        assertFalse(summary.upcomingPlaces().stream().anyMatch(p -> p.id().equals("p2")));
    }

    @Test
    @DisplayName("Passed attractions: places behind the traveler are excluded from upcoming")
    void testPassedAttractions_BehindTravelerAreExcluded() {
        RouteInfo route = createSampleNorthboundRoute();

        // Traveler has moved forward to lat 17.3600 (~6.6km along route)
        double userLat = 17.3600;
        double userLon = 78.4800;

        // Place Behind: at lat 17.3200 (~4.4km behind user)
        Place placeBehind = new Place("p_behind", "Passed Monument", "monument", "Already passed",
                17.3200, 78.4802, 4400.0, 9);

        // Place Ahead: at lat 17.3800 (~2.2km ahead of user)
        Place placeAhead = new Place("p_ahead", "Upcoming Temple", "cultural_temple", "Ahead on route",
                17.3800, 78.4802, 2200.0, 9);

        RouteService.ProjectionResult projUser = routeService.projectPointOnPolyline(userLat, userLon, route.geometry());
        RouteService.ClassifiedRouteSummary summary = routeService.classifyPlacesAlongRoute(
                route,
                projUser.alongTrackDistanceMeters(),
                userLat,
                userLon,
                0.0,
                15.0,
                List.of(placeBehind, placeAhead)
        );

        // Passed place must be in passedPlaces and NOT in upcomingPlaces
        assertTrue(summary.passedPlaces().stream().anyMatch(p -> p.id().equals("p_behind")));
        assertFalse(summary.upcomingPlaces().stream().anyMatch(p -> p.id().equals("p_behind")));

        // Ahead place must be upcoming
        assertTrue(summary.upcomingPlaces().stream().anyMatch(p -> p.id().equals("p_ahead")));
        assertNotNull(summary.nextUpcomingPlace());
        assertEquals("p_ahead", summary.nextUpcomingPlace().id());
        assertTrue(summary.isNextPlaceUpcoming());
    }

    @Test
    @DisplayName("Heading uncertainty: handles low speed, null heading, and unreliable values gracefully")
    void testHeadingUncertainty_LowSpeedAndNullHeading() {
        // Low speed (< 1.5 m/s) -> heading is unreliable
        assertFalse(routeService.isHeadingReliable(90.0, 0.8));
        assertFalse(routeService.isHeadingReliable(90.0, 0.0));

        // Null heading or negative values -> unreliable
        assertFalse(routeService.isHeadingReliable(null, 15.0));
        assertFalse(routeService.isHeadingReliable(-1.0, 15.0));
        assertFalse(routeService.isHeadingReliable(400.0, 15.0));

        // Normal speed with valid heading -> reliable
        assertTrue(routeService.isHeadingReliable(90.0, 15.0));
        assertTrue(routeService.isHeadingReliable(0.0, 2.0));

        // When heading is null and speed is null, classifyPlacesAlongRoute still runs safely without crashing
        RouteInfo route = createSampleNorthboundRoute();
        Place place = new Place("p1", "Test Landmark", "fort_palace", "Note", 17.3500, 78.4800, 2000.0, 10);
        assertDoesNotThrow(() -> {
            RouteService.ClassifiedRouteSummary res = routeService.classifyPlacesAlongRoute(
                    route, 0.0, 17.3000, 78.4800, null, null, List.of(place)
            );
            assertNotNull(res);
            assertEquals(1, res.upcomingPlaces().size());
        });
    }

    @Test
    @DisplayName("Route-aware story trigger: triggers ROUTE_UPCOMING_LANDMARK within lead distance")
    void testStoryTrigger_RouteUpcomingLandmark() {
        JourneyState journeyState = storyTriggerService.getOrCreateJourney("test_journey_route");
        RouteInfo route = createSampleNorthboundRoute();
        journeyState.setCurrentRoute(route);
        journeyState.updateMotion(17.3000, 78.4800, 0.0, 15.0);

        // Major landmark located ~1.8km ahead on route (lat 17.3160)
        Place landmarkAhead = new Place("landmark_1", "Golconda Citadel", "fort_palace", "Majestic citadel",
                17.3160, 78.4802, 1800.0, 10);

        StoryTriggerService.TriggerDecision decision = storyTriggerService.evaluateTrigger(
                journeyState,
                "Hyderabad",
                "Telangana",
                List.of(landmarkAhead),
                route,
                0.0,
                15.0,
                Instant.now()
        );

        assertTrue(decision.triggerStory());
        assertEquals(JourneyEvent.ROUTE_UPCOMING_LANDMARK, decision.event());
        assertEquals("landmark_1", decision.targetPlace().id());
        assertTrue(decision.reason().contains("North City"));
    }

    @Test
    @DisplayName("Duplicate suppression: already narrated place along route is never re-triggered")
    void testDuplicateSuppression_RouteAttractionNotRepeated() {
        JourneyState journeyState = storyTriggerService.getOrCreateJourney("test_journey_dedup");
        RouteInfo route = createSampleNorthboundRoute();
        journeyState.setCurrentRoute(route);
        journeyState.updateMotion(17.3000, 78.4800, 0.0, 15.0);

        Place landmarkAhead = new Place("landmark_dedup", "Warangal Fort Ruins", "fort_palace", "Historic gateway",
                17.3160, 78.4802, 1800.0, 10);

        // 1. Initial trigger
        StoryTriggerService.TriggerDecision decision1 = storyTriggerService.evaluateTrigger(
                journeyState, "Warangal", "Telangana", List.of(landmarkAhead), route, 0.0, 15.0, Instant.now()
        );
        assertTrue(decision1.triggerStory());

        // Record narration
        storyTriggerService.recordNarration(journeyState, "Warangal", "Telangana", landmarkAhead, "Story text", Instant.now());

        // 2. Subsequent ping 10 minutes later (past cooldown) on same route
        Instant future = Instant.now().plusSeconds(600);
        StoryTriggerService.TriggerDecision decision2 = storyTriggerService.evaluateTrigger(
                journeyState, "Warangal", "Telangana", List.of(landmarkAhead), route, 0.0, 15.0, future
        );

        // Must NOT trigger again because it was already narrated
        assertFalse(decision2.triggerStory());
    }

    @Test
    @DisplayName("Missing route data: gracefully falls back to standard nearby POI behavior")
    void testMissingRouteData_FallbackToStandardNearby() {
        JourneyState journeyState = storyTriggerService.getOrCreateJourney("test_no_route");
        journeyState.clearRoute(); // No route set
        journeyState.updateCoordinates(17.3850, 78.4866);

        Place nearbyPlace = new Place("p_nearby", "Charminar", "monument", "Historic monument",
                17.3860, 78.4870, 800.0, 9);

        // Journey without route evaluates standard proximity POIs
        StoryTriggerService.TriggerDecision decision = storyTriggerService.evaluateTrigger(
                journeyState,
                "Hyderabad",
                "Telangana",
                List.of(nearbyPlace),
                null, // No RouteInfo
                null, // No heading
                null, // No speed
                Instant.now()
        );

        assertTrue(decision.triggerStory());
        assertEquals(JourneyEvent.NEARBY_MAJOR_LANDMARK, decision.event());
        assertEquals("p_nearby", decision.targetPlace().id());
    }

    @Test
    @DisplayName("Routing provider resilience: handles provider failure with fallback straight-line route")
    void testRoutingProviderFailure_FallbackRoute() {
        RoutingProvider failingProvider = (startLat, startLon, destLat, destLon, destName) -> {
            throw new RuntimeException("Simulated OSRM timeout / 503 Service Unavailable");
        };

        RouteService customRouteService = new RouteService(failingProvider, geocodingService);
        JourneyState state = new JourneyState("test_fail_resilience");

        // Request route with failing provider
        assertDoesNotThrow(() -> {
            RouteInfo route = customRouteService.setupRoute(state, 17.3850, 78.4866, "17.4850, 78.4866", 17.4850, 78.4866);
            // Must gracefully return null or fallback without throwing exception
        });
    }

    @Test
    @DisplayName("Full Journey Location Ping: integrates route, heading, speed into response")
    void testFullJourneyLocationPing_WithRoute() {
        String journeyId = "test_ping_with_route";
        journeyService.resetJourney(journeyId);

        // Set route to Warangal
        journeyService.setJourneyRoute(journeyId, "Warangal", 17.9820, 79.5970, 17.3850, 78.4866);

        JourneyLocationRequest request = new JourneyLocationRequest(
                journeyId,
                17.3850,
                78.4866,
                Instant.now(),
                true,
                45.0,  // heading
                18.0,  // speed 18 m/s (~65 km/h)
                "Warangal",
                17.9820,
                79.5970
        );

        JourneyLocationResponse response = assertDoesNotThrow(() -> journeyService.processLocation(request));
        assertNotNull(response);
        assertNotNull(response.routeInfo());
        assertEquals("Warangal", response.routeInfo().destinationName());
        assertTrue(response.routeInfo().totalDistanceMeters() > 0);
    }
}
