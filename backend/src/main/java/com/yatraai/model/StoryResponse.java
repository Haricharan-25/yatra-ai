package com.yatraai.model;

public record StoryResponse(
    String city,
    String story,
    int durationSeconds,
    String placeName,
    String category,
    JourneyEvent triggerReason
) {
    public StoryResponse(String city, String story, int durationSeconds) {
        this(city, story, durationSeconds, null, null, null);
    }
}
