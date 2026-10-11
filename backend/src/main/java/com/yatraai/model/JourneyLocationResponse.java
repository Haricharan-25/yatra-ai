package com.yatraai.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record JourneyLocationResponse(
    String journeyId,
    String city,
    String state,
    String country,
    String displayName,
    List<Place> nearbyPlaces,
    Place topPlace,
    Weather weather,
    boolean triggerStory,
    JourneyEvent triggerReason,
    List<JourneyEvent> detectedEvents,
    String story,
    int durationSeconds,
    LocationContext locationContext,
    RouteInfo routeInfo,
    Place upcomingRoutePlace
) {
    /**
     * Alias for whether narration is recommended.
     */
    @JsonProperty("narrationRecommended")
    public boolean narrationRecommended() {
        return triggerStory;
    }

    /**
     * Constructor without routeInfo (backward-compatible).
     */
    public JourneyLocationResponse(
            String journeyId,
            String city,
            String state,
            String country,
            String displayName,
            List<Place> nearbyPlaces,
            Place topPlace,
            Weather weather,
            boolean triggerStory,
            JourneyEvent triggerReason,
            List<JourneyEvent> detectedEvents,
            String story,
            int durationSeconds,
            LocationContext locationContext
    ) {
        this(
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
                story,
                durationSeconds,
                locationContext,
                null,
                null
        );
    }

    /**
     * Backward-compatible constructor for existing tests and endpoints.
     */
    public JourneyLocationResponse(
            String city,
            String state,
            String country,
            String displayName,
            List<Place> nearbyPlaces,
            Place topPlace,
            Weather weather,
            boolean triggerStory,
            JourneyEvent triggerReason,
            String story,
            int durationSeconds
    ) {
        this(
                null,
                city,
                state,
                country,
                displayName,
                nearbyPlaces,
                topPlace,
                weather,
                triggerStory,
                triggerReason,
                triggerReason != null ? List.of(triggerReason) : List.of(),
                story,
                durationSeconds,
                null,
                null,
                null
        );
    }
}
