package com.yatraai.model;

import java.util.List;

public record LocationContext(
    LocationPoint location,
    String city,
    String state,
    String country,
    String displayName,
    List<Place> nearbyPlaces,
    Place topPlace,
    Weather weather,
    List<String> previousStories,
    long timeSinceLastStorySeconds,
    boolean triggerStory,
    JourneyEvent triggerReason,
    String generatedStory
) {}
