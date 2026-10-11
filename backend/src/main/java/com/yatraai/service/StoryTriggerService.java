package com.yatraai.service;

import com.yatraai.model.JourneyEvent;
import com.yatraai.model.JourneyState;
import com.yatraai.model.Place;
import com.yatraai.model.RouteInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class StoryTriggerService {

    private static final Logger logger = LoggerFactory.getLogger(StoryTriggerService.class);

    @Value("${journey.story.cooldown-seconds:300}")
    private long cooldownSeconds = 300; // default 5 minutes (300 seconds)

    @Value("${places.trigger-distance.major-landmark:2000.0}")
    private double majorLandmarkTriggerDistance = 2000.0;

    @Value("${places.trigger-distance.normal-attraction:1000.0}")
    private double normalAttractionTriggerDistance = 1000.0;

    @Value("${places.trigger-distance.scenic-feature:1500.0}")
    private double scenicFeatureTriggerDistance = 1500.0;

    private final RouteService routeService;

    // Multi-journey state store in memory
    private final Map<String, JourneyState> journeys = new ConcurrentHashMap<>();

    // Default journey state for single-journey / backward-compatible callers
    private final JourneyState defaultJourney = new JourneyState("default");

    public record TriggerDecision(
            boolean triggerStory,
            JourneyEvent event,
            Place targetPlace,
            String reason
    ) {}

    public StoryTriggerService() {
        this(null);
    }

    @Autowired
    public StoryTriggerService(@Autowired(required = false) RouteService routeService) {
        this.routeService = routeService;
    }

    public JourneyState getOrCreateJourney(String journeyId) {
        if (journeyId == null || journeyId.isBlank() || journeyId.equalsIgnoreCase("default")) {
            return defaultJourney;
        }
        return journeys.computeIfAbsent(journeyId, JourneyState::new);
    }

    /**
     * Backward-compatible trigger evaluation using default journey state.
     */
    public synchronized TriggerDecision evaluateTrigger(
            String currentCity,
            String currentState,
            List<Place> nearbyPlaces,
            Instant requestTime
    ) {
        return evaluateTrigger(defaultJourney, currentCity, currentState, nearbyPlaces, requestTime);
    }

    /**
     * Backward-compatible trigger evaluation without explicit route parameters.
     */
    public synchronized TriggerDecision evaluateTrigger(
            JourneyState state,
            String currentCity,
            String currentState,
            List<Place> nearbyPlaces,
            Instant requestTime
    ) {
        return evaluateTrigger(
                state,
                currentCity,
                currentState,
                nearbyPlaces,
                state != null ? state.getCurrentRoute() : null,
                state != null ? state.getLatestHeading() : null,
                state != null ? state.getLatestSpeed() : null,
                requestTime
        );
    }

    /**
     * Context-aware and Route-Aware evaluation of whether YatraAI should trigger an audio story:
     * Priority:
     * 0. Upcoming Major Landmark along Active Route (ROUTE_UPCOMING_LANDMARK: Priority A+)
     * 1. Upcoming Attraction along Active Route (ROUTE_UPCOMING_ATTRACTION: Priority A)
     * 2. Unvisited Major Historical Landmark / Fort / Palace within <= 2km (Priority B)
     * 3. Unvisited Significant Natural Feature within <= 1.5km (Priority C)
     * 4. Unvisited Cultural Landmark / Attraction within <= 1km (Priority D)
     * 5. State / Region Transition (REGION_CHANGED)
     * 6. City Change (CITY_CHANGED)
     * 7. Initial City Arrival (CITY_ENTERED)
     */
    public synchronized TriggerDecision evaluateTrigger(
            JourneyState state,
            String currentCity,
            String currentState,
            List<Place> nearbyPlaces,
            RouteInfo routeInfo,
            Double heading,
            Double speed,
            Instant requestTime
    ) {
        if (state == null) {
            state = defaultJourney;
        }

        // If journey is paused or stopped, do not trigger narrations
        if (state.isPaused()) {
            return new TriggerDecision(false, JourneyEvent.NONE, null, "Journey is paused");
        }
        if (state.isStopped()) {
            return new TriggerDecision(false, JourneyEvent.NONE, null, "Journey is stopped");
        }

        Instant now = requestTime != null ? requestTime : Instant.now();
        long timeSinceLastStory = state.getTimeSinceLastStorySeconds(now);

        // --- 0. ROUTE-AWARE ADVANCE NARRATION (When route data is present) ---
        if (state.hasRoute() && routeInfo != null && routeService != null && routeInfo.geometry() != null && !routeInfo.geometry().isEmpty()) {
            double currentLat = state.getLastLatitude() != null ? state.getLastLatitude() : 0.0;
            double currentLon = state.getLastLongitude() != null ? state.getLastLongitude() : 0.0;

            RouteService.ProjectionResult userProj = routeService.projectPointOnPolyline(
                    currentLat, currentLon, routeInfo.geometry()
            );
            double userProgress = userProj.alongTrackDistanceMeters();

            RouteService.ClassifiedRouteSummary routeSummary = routeService.classifyPlacesAlongRoute(
                    routeInfo,
                    userProgress,
                    currentLat,
                    currentLon,
                    heading,
                    speed,
                    nearbyPlaces
            );

            Place topRoutePlace = null;
            double topRouteDist = 0.0;
            JourneyEvent routeEvent = JourneyEvent.NONE;

            if (routeSummary.upcomingPlaces() != null) {
                for (Place p : routeSummary.upcomingPlaces()) {
                    if (p == null || state.hasNarratedPlace(p)) {
                        continue;
                    }
                    RouteService.ProjectionResult placeProj = routeService.projectPointOnPolyline(
                            p.latitude(), p.longitude(), routeInfo.geometry()
                    );
                    double distAhead = placeProj.alongTrackDistanceMeters() - userProgress;

                    if (routeService.isEligibleForAdvanceNarration(p, distAhead)) {
                        topRoutePlace = p;
                        topRouteDist = distAhead;
                        routeEvent = p.importanceScore() >= 9
                                ? JourneyEvent.ROUTE_UPCOMING_LANDMARK
                                : JourneyEvent.ROUTE_UPCOMING_ATTRACTION;
                        break;
                    }
                }
            }

            if (routeEvent != JourneyEvent.NONE && topRoutePlace != null) {
                if (state.getLastNarrationTime() == null || timeSinceLastStory >= cooldownSeconds) {
                    String eventType = routeEvent == JourneyEvent.ROUTE_UPCOMING_LANDMARK ? "major landmark" : "attraction";
                    return new TriggerDecision(
                            true,
                            routeEvent,
                            topRoutePlace,
                            String.format(Locale.US, "Approaching %s %s on route to %s (%.0fm ahead)",
                                    eventType, topRoutePlace.name(), routeInfo.destinationName(), topRouteDist)
                    );
                } else {
                    logger.info("Route place {} nearby, but cooldown active ({}s / {}s)",
                            topRoutePlace.name(), timeSinceLastStory, cooldownSeconds);
                    return new TriggerDecision(false, JourneyEvent.NONE, null, "Cooldown active for route attraction: " + topRoutePlace.name());
                }
            }
        }

        // --- 1. Detect Geographic Region Transitions ---
        boolean isInitialCity = false;
        boolean isCityChange = false;
        boolean isRegionChange = false;

        String prevCity = state.getPreviousCity();
        String prevState = state.getPreviousState();

        if (currentCity != null && !currentCity.isBlank() && !currentCity.equalsIgnoreCase("Unknown Location")) {
            if (prevCity == null) {
                isInitialCity = true;
            } else if (!prevCity.equalsIgnoreCase(currentCity)) {
                isCityChange = true;
            }
        }

        if (currentState != null && !currentState.isBlank() && prevState != null && !prevState.isBlank()) {
            if (!prevState.equalsIgnoreCase(currentState)) {
                isRegionChange = true;
            }
        }

        // --- 2. Standard Proximity POI Discovery (When not on a route or no route place in range) ---
        Place candidatePlace = findTopUnvisitedPlace(state, nearbyPlaces);

        JourneyEvent poiEvent = JourneyEvent.NONE;
        boolean isNatural = false;

        if (candidatePlace != null) {
            double distance = candidatePlace.distanceMeters();
            int score = candidatePlace.importanceScore();
            String cat = candidatePlace.category() != null ? candidatePlace.category().toLowerCase() : "";
            isNatural = cat.contains("natural") || cat.contains("water") || cat.contains("lake") || cat.contains("river");

            if (score >= 9 && distance <= majorLandmarkTriggerDistance) {
                poiEvent = JourneyEvent.NEARBY_MAJOR_LANDMARK;
            } else if (isNatural && distance <= scenicFeatureTriggerDistance) {
                poiEvent = JourneyEvent.NEARBY_ATTRACTION;
            } else if (score >= 6 && distance <= normalAttractionTriggerDistance) {
                poiEvent = JourneyEvent.NEARBY_ATTRACTION;
            }
        }

        // Priority 1: Unvisited Major Landmark (Forts, Palaces, Citadels, Monuments)
        if (poiEvent == JourneyEvent.NEARBY_MAJOR_LANDMARK) {
            if (state.getLastNarrationTime() == null || timeSinceLastStory >= cooldownSeconds) {
                return new TriggerDecision(
                        true,
                        JourneyEvent.NEARBY_MAJOR_LANDMARK,
                        candidatePlace,
                        String.format("Approaching major landmark %s (%.0fm away)", candidatePlace.name(), candidatePlace.distanceMeters())
                );
            } else {
                logger.info("Major landmark {} nearby, but cooldown active ({}s / {}s)",
                        candidatePlace.name(), timeSinceLastStory, cooldownSeconds);
                return new TriggerDecision(false, JourneyEvent.NONE, null, "Cooldown active for landmark: " + candidatePlace.name());
            }
        }

        // Priority 2: Unvisited Significant Natural Feature
        if (poiEvent == JourneyEvent.NEARBY_ATTRACTION && isNatural) {
            if (state.getLastNarrationTime() == null || timeSinceLastStory >= cooldownSeconds) {
                return new TriggerDecision(
                        true,
                        JourneyEvent.NEARBY_ATTRACTION,
                        candidatePlace,
                        String.format("Approaching natural attraction %s (%.0fm away)", candidatePlace.name(), candidatePlace.distanceMeters())
                );
            } else {
                logger.info("Natural attraction {} nearby, but cooldown active ({}s / {}s)",
                        candidatePlace.name(), timeSinceLastStory, cooldownSeconds);
                return new TriggerDecision(false, JourneyEvent.NONE, null, "Cooldown active for natural attraction: " + candidatePlace.name());
            }
        }

        // Priority 3: Cultural Landmarks & Heritage Attractions
        if (poiEvent == JourneyEvent.NEARBY_ATTRACTION) {
            if (state.getLastNarrationTime() == null || timeSinceLastStory >= cooldownSeconds) {
                return new TriggerDecision(
                        true,
                        JourneyEvent.NEARBY_ATTRACTION,
                        candidatePlace,
                        String.format("Nearby attraction %s (%.0fm away)", candidatePlace.name(), candidatePlace.distanceMeters())
                );
            } else {
                logger.info("Attraction {} nearby, but cooldown active ({}s / {}s)",
                        candidatePlace.name(), timeSinceLastStory, cooldownSeconds);
                return new TriggerDecision(false, JourneyEvent.NONE, null, "Cooldown active for attraction: " + candidatePlace.name());
            }
        }

        // Priority 4: Region Transition
        if (isRegionChange) {
            if (state.getLastNarrationTime() == null || timeSinceLastStory >= Math.min(cooldownSeconds, 60)) {
                return new TriggerDecision(
                        true,
                        JourneyEvent.REGION_CHANGED,
                        null,
                        String.format("Crossed regional boundary into %s (previously in %s)", currentState, prevState)
                );
            }
        }

        // Priority 5: City Change
        if (isCityChange) {
            if (state.getLastNarrationTime() == null || timeSinceLastStory >= Math.min(cooldownSeconds, 60)) {
                return new TriggerDecision(
                        true,
                        JourneyEvent.CITY_CHANGED,
                        null,
                        String.format("Entered new city %s (previously in %s)", currentCity, prevCity)
                );
            }
        }

        // Priority 6: Initial City Arrival
        if (isInitialCity) {
            return new TriggerDecision(
                    true,
                    JourneyEvent.CITY_ENTERED,
                    null,
                    "First location detection of journey in " + currentCity
            );
        }

        return new TriggerDecision(false, JourneyEvent.NONE, null, "No trigger condition met or cooldown active");
    }

    public synchronized Place findTopUnvisitedPlace(List<Place> places) {
        return findTopUnvisitedPlace(defaultJourney, places);
    }

    public synchronized Place findTopUnvisitedPlace(JourneyState state, List<Place> places) {
        if (places == null || places.isEmpty()) {
            return null;
        }

        Place bestCandidate = null;
        double bestScore = -1.0;

        for (Place place : places) {
            if (place == null) {
                continue;
            }
            if (state != null && state.hasNarratedPlace(place)) {
                continue;
            }
            if (!isPlaceInRange(place)) {
                continue;
            }

            double priorityScore = calculatePlacePriorityScore(place);
            if (priorityScore > bestScore) {
                bestScore = priorityScore;
                bestCandidate = place;
            } else if (Math.abs(priorityScore - bestScore) < 0.001 && bestCandidate != null) {
                String curKey = bestCandidate.id() != null ? bestCandidate.id() : bestCandidate.name();
                String newKey = place.id() != null ? place.id() : place.name();
                if (newKey != null && curKey != null && newKey.compareTo(curKey) < 0) {
                    bestCandidate = place;
                }
            }
        }

        return bestCandidate;
    }

    public boolean isPlaceInRange(Place place) {
        if (place == null) return false;
        double dist = place.distanceMeters();
        int score = place.importanceScore();
        String cat = place.category() != null ? place.category().toLowerCase() : "";
        boolean isNatural = cat.contains("natural") || cat.contains("water") || cat.contains("lake") || cat.contains("river");

        if (score >= 9 && dist <= majorLandmarkTriggerDistance) {
            return true;
        }
        if (isNatural && dist <= scenicFeatureTriggerDistance) {
            return true;
        }
        if (score >= 6 && dist <= normalAttractionTriggerDistance) {
            return true;
        }
        return false;
    }

    public double calculatePlacePriorityScore(Place place) {
        int score = place.importanceScore();
        double dist = place.distanceMeters();
        String cat = place.category() != null ? place.category().toLowerCase() : "";
        boolean isNatural = cat.contains("natural") || cat.contains("water") || cat.contains("lake");

        double base = score * 1000.0;
        double maxDist = score >= 9
                ? majorLandmarkTriggerDistance
                : (isNatural ? scenicFeatureTriggerDistance : normalAttractionTriggerDistance);
        double proximityBonus = Math.max(0.0, (1.0 - (dist / Math.max(maxDist, 1.0))) * 500.0);

        return base + proximityBonus;
    }

    public synchronized void recordNarration(String city, String state, Place place, String storyText) {
        recordNarration(defaultJourney, city, state, place, storyText, Instant.now());
    }

    public synchronized void recordNarration(String city, String state, Place place, String storyText, Instant narrationTime) {
        recordNarration(defaultJourney, city, state, place, storyText, narrationTime);
    }

    public synchronized void recordNarration(JourneyState journeyState, String city, String state, Place place, String storyText, Instant narrationTime) {
        JourneyState target = journeyState != null ? journeyState : defaultJourney;
        target.recordNarration(city, state, place, storyText, narrationTime);
        logger.info("Recorded narration on journey '{}': city={}, place={}, totalNarratedPlaces={}, time={}",
                target.getJourneyId(), city, place != null ? place.name() : "none",
                target.getNarratedPlaceNames().size(), target.getLastNarrationTime());
    }

    public synchronized void resetJourney() {
        defaultJourney.reset();
        journeys.clear();
        logger.info("In-memory journey state successfully reset.");
    }

    public synchronized void resetJourney(String journeyId) {
        if (journeyId == null || journeyId.isBlank() || journeyId.equalsIgnoreCase("default")) {
            defaultJourney.reset();
        } else {
            JourneyState state = journeys.get(journeyId);
            if (state != null) {
                state.reset();
            }
        }
    }

    public synchronized long getTimeSinceLastStorySeconds(Instant now) {
        return defaultJourney.getTimeSinceLastStorySeconds(now);
    }

    public synchronized String getPreviousCity() {
        return defaultJourney.getPreviousCity();
    }

    public synchronized void setPreviousCity(String city) {
        defaultJourney.setPreviousCity(city);
    }

    public synchronized void setLastNarrationTime(Instant time) {
        defaultJourney.setLastNarrationTime(time);
    }

    public synchronized Set<String> getNarratedPlaceNames() {
        return defaultJourney.getNarratedPlaceNames();
    }

    public synchronized List<String> getPreviousStories() {
        return defaultJourney.getPreviousStories();
    }

    public long getCooldownSeconds() {
        return cooldownSeconds;
    }

    public void setCooldownSeconds(long cooldownSeconds) {
        this.cooldownSeconds = cooldownSeconds;
    }

    public RouteService getRouteService() {
        return routeService;
    }
}
