package com.yatraai.service;

import com.yatraai.model.JourneyEvent;
import com.yatraai.model.JourneyLocationRequest;
import com.yatraai.model.JourneyLocationResponse;
import com.yatraai.model.JourneyState;
import com.yatraai.model.LocationContext;
import com.yatraai.model.LocationPoint;
import com.yatraai.model.LocationResponse;
import com.yatraai.model.Place;
import com.yatraai.model.RouteInfo;
import com.yatraai.model.StoryResponse;
import com.yatraai.model.Weather;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

@Service
public class JourneyService {

    private static final Logger logger = LoggerFactory.getLogger(JourneyService.class);

    private final GeocodingService geocodingService;
    private final PlacesService placesService;
    private final WeatherService weatherService;
    private final StoryTriggerService storyTriggerService;
    private final StoryService storyService;
    private final RouteService routeService;

    @Autowired
    public JourneyService(
            GeocodingService geocodingService,
            PlacesService placesService,
            WeatherService weatherService,
            StoryTriggerService storyTriggerService,
            StoryService storyService,
            @Autowired(required = false) RouteService routeService
    ) {
        this.geocodingService = geocodingService;
        this.placesService = placesService;
        this.weatherService = weatherService;
        this.storyTriggerService = storyTriggerService;
        this.storyService = storyService;
        this.routeService = routeService;
    }

    public JourneyService(
            GeocodingService geocodingService,
            PlacesService placesService,
            WeatherService weatherService,
            StoryTriggerService storyTriggerService,
            StoryService storyService
    ) {
        this(geocodingService, placesService, weatherService, storyTriggerService, storyService, null);
    }

    /**
     * Processes a continuous journey location update:
     * 1. Resolves city/region via cached reverse geocoding
     * 2. Finds nearby POIs and prioritizes them
     * 3. Evaluates optional route progress and upcoming corridor attractions
     * 4. Fetches live weather context
     * 5. Evaluates if a new story should be triggered
     * 6. If triggered, generates a context-aware travel story
     */
    public JourneyLocationResponse processLocation(JourneyLocationRequest request) {
        double lat = request.latitude();
        double lon = request.longitude();
        Instant timestamp = request.timestamp() != null ? request.timestamp() : Instant.now();
        String journeyId = request.journeyId() != null && !request.journeyId().isBlank() ? request.journeyId() : "default";

        JourneyState journeyState = storyTriggerService.getOrCreateJourney(journeyId);

        logger.info("JourneyService: Processing location update for journey '{}' at lat={}, lon={}, heading={}, speed={}, status={}",
                journeyId, lat, lon, request.heading(), request.speed(), journeyState.getStatus());

        // Update latest coordinates and motion
        journeyState.updateMotion(lat, lon, request.heading(), request.speed());

        // Optional route setup if destination specified in request and route not yet created
        if (request.destination() != null && !request.destination().isBlank() && routeService != null) {
            String requestedDest = request.destination().trim();
            if (journeyState.getCurrentRoute() == null ||
                    !requestedDest.equalsIgnoreCase(journeyState.getDestination())) {
                routeService.setupRoute(
                        journeyState,
                        lat,
                        lon,
                        requestedDest,
                        request.destinationLat(),
                        request.destinationLon()
                );
            }
        }

        // 1. Reverse Geocode (with cache)
        LocationResponse loc = geocodingService.reverseGeocode(lat, lon);
        String city = loc.city() != null ? loc.city() : "Unknown Location";
        String state = loc.state() != null ? loc.state() : "";
        String country = loc.country() != null ? loc.country() : "";
        String displayName = loc.displayName() != null ? loc.displayName() : city;

        // If journey is paused or stopped, do not trigger stories
        if (journeyState.isPaused() || journeyState.isStopped()) {
            logger.info("Journey '{}' is {} - returning location context without triggering stories",
                    journeyId, journeyState.getStatus());

            LocationContext context = new LocationContext(
                    new LocationPoint(lat, lon),
                    city,
                    state,
                    country,
                    displayName,
                    List.of(),
                    null,
                    null,
                    journeyState.getPreviousStories(),
                    journeyState.getTimeSinceLastStorySeconds(timestamp),
                    false,
                    JourneyEvent.NONE,
                    null
            );

            return new JourneyLocationResponse(
                    journeyId,
                    city,
                    state,
                    country,
                    displayName,
                    List.of(),
                    null,
                    null,
                    false,
                    JourneyEvent.NONE,
                    List.of(),
                    null,
                    0,
                    context,
                    journeyState.getCurrentRoute(),
                    null
            );
        }

        // 2. Query nearby POIs within 2500m
        List<Place> nearbyPlaces = Collections.emptyList();
        try {
            nearbyPlaces = placesService.getNearbyPlaces(lat, lon, 2500.0);
        } catch (Exception e) {
            logger.warn("Places lookup failed: {}. Continuing without POIs.", e.getMessage());
        }

        // 3. Update route progress and corridor classification if route is active
        if (journeyState.hasRoute() && routeService != null) {
            RouteInfo updatedRoute = routeService.updateRouteProgress(
                    journeyState.getCurrentRoute(),
                    lat,
                    lon,
                    request.heading(),
                    request.speed(),
                    nearbyPlaces
            );
            journeyState.setCurrentRoute(updatedRoute);
        }

        // 4. Query current weather
        Weather weather = null;
        try {
            weather = weatherService.getCurrentWeather(lat, lon);
        } catch (Exception e) {
            logger.warn("Weather lookup failed: {}. Continuing without weather.", e.getMessage());
        }

        // 5. Evaluate trigger engine against this journey session
        StoryTriggerService.TriggerDecision decision = storyTriggerService.evaluateTrigger(
                journeyState,
                city,
                state,
                nearbyPlaces,
                journeyState.getCurrentRoute(),
                request.heading(),
                request.speed(),
                timestamp
        );

        boolean triggerStory = decision.triggerStory();
        JourneyEvent triggerReason = decision.event();
        Place targetPlace = decision.targetPlace();

        String generatedStory = null;
        int durationSeconds = 0;

        // 6. If trigger condition is met, generate contextual story
        if (triggerStory) {
            logger.info("Triggering story for journey '{}': event={}, place={}, reason={}",
                    journeyId, triggerReason, targetPlace != null ? targetPlace.name() : "none", decision.reason());

            StoryResponse storyResp = (journeyState.getDestination() != null && !journeyState.getDestination().isBlank())
                    ? storyService.generateContextualStory(
                            city,
                            state,
                            country,
                            targetPlace,
                            weather,
                            journeyState.getPreviousStories(),
                            triggerReason,
                            journeyState.getDestination()
                    )
                    : storyService.generateContextualStory(
                            city,
                            state,
                            country,
                            targetPlace,
                            weather,
                            journeyState.getPreviousStories(),
                            triggerReason
                    );

            generatedStory = storyResp.story();
            durationSeconds = storyResp.durationSeconds();

            // Record this narration in memory to prevent duplicates and reset cooldown
            storyTriggerService.recordNarration(journeyState, city, state, targetPlace, generatedStory, timestamp);
        }

        Place topPlace = !nearbyPlaces.isEmpty() ? nearbyPlaces.get(0) : null;
        RouteInfo activeRoute = journeyState.getCurrentRoute();
        Place upcomingRoutePlace = activeRoute != null ? activeRoute.nextUpcomingPlace() : null;

        LocationContext locationContext = new LocationContext(
                new LocationPoint(lat, lon),
                city,
                state,
                country,
                displayName,
                nearbyPlaces,
                topPlace,
                weather,
                journeyState.getPreviousStories(),
                journeyState.getTimeSinceLastStorySeconds(timestamp),
                triggerStory,
                triggerReason,
                generatedStory
        );

        List<JourneyEvent> detectedEvents = triggerReason != JourneyEvent.NONE ? List.of(triggerReason) : List.of();

        return new JourneyLocationResponse(
                journeyId,
                city,
                state,
                country,
                displayName,
                nearbyPlaces,
                topPlace,
                weather,
                triggerStory,
                triggerReason,
                detectedEvents,
                generatedStory,
                durationSeconds,
                locationContext,
                activeRoute,
                upcomingRoutePlace
        );
    }

    public RouteInfo setJourneyRoute(String journeyId, String destination, Double destLat, Double destLon, double originLat, double originLon) {
        if (routeService == null || destination == null || destination.isBlank()) {
            return null;
        }
        JourneyState state = storyTriggerService.getOrCreateJourney(journeyId);
        RouteInfo route = routeService.setupRoute(state, originLat, originLon, destination, destLat, destLon);
        logger.info("Route established for journey '{}' to '{}': totalDistance={}m",
                journeyId, destination, route != null ? route.totalDistanceMeters() : 0);
        return route;
    }

    public void clearJourneyRoute(String journeyId) {
        JourneyState state = storyTriggerService.getOrCreateJourney(journeyId);
        state.clearRoute();
        logger.info("Route cleared for journey '{}'", journeyId);
    }

    public JourneyState startJourney(String journeyId) {
        JourneyState state = storyTriggerService.getOrCreateJourney(journeyId);
        state.resume();
        logger.info("Journey '{}' started / resumed", state.getJourneyId());
        return state;
    }

    public JourneyState pauseJourney(String journeyId) {
        JourneyState state = storyTriggerService.getOrCreateJourney(journeyId);
        state.pause();
        logger.info("Journey '{}' paused", state.getJourneyId());
        return state;
    }

    public JourneyState resumeJourney(String journeyId) {
        JourneyState state = storyTriggerService.getOrCreateJourney(journeyId);
        state.resume();
        logger.info("Journey '{}' resumed", state.getJourneyId());
        return state;
    }

    public JourneyState stopJourney(String journeyId) {
        JourneyState state = storyTriggerService.getOrCreateJourney(journeyId);
        state.stop();
        logger.info("Journey '{}' stopped", state.getJourneyId());
        return state;
    }

    public LocationContext getLocationContext(JourneyLocationRequest request) {
        JourneyLocationResponse resp = processLocation(request);
        return resp.locationContext();
    }

    public void resetJourney() {
        storyTriggerService.resetJourney();
    }

    public void resetJourney(String journeyId) {
        storyTriggerService.resetJourney(journeyId);
    }
}
