package com.yatraai.model;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record StoryRequest(
    @NotBlank(message = "City cannot be empty")
    String city,
    String state,
    String country,
    Place targetPlace,
    Weather weather,
    JourneyEvent triggerReason,
    List<String> previousStories
) {
    public StoryRequest(String city, String state, String country) {
        this(city, state, country, null, null, null, null);
    }
}
