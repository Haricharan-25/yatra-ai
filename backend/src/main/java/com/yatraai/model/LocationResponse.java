package com.yatraai.model;

public record LocationResponse(
    Double latitude,
    Double longitude,
    String city,
    String state,
    String country,
    String displayName
) {
    public LocationResponse(String city, String state, String country, String displayName) {
        this(null, null, city, state, country, displayName);
    }
}
