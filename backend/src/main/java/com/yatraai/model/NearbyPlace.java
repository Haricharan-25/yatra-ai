package com.yatraai.model;

/**
 * Architectural alias and model representation for nearby discovered attractions.
 */
public record NearbyPlace(
    String id,
    String name,
    String category,
    String description,
    double latitude,
    double longitude,
    double distanceMeters,
    int importanceScore
) {
    public static NearbyPlace fromPlace(Place place) {
        if (place == null) return null;
        return new NearbyPlace(
                place.id(),
                place.name(),
                place.category(),
                place.description(),
                place.latitude(),
                place.longitude(),
                place.distanceMeters(),
                place.importanceScore()
        );
    }

    public Place toPlace() {
        return new Place(
                id,
                name,
                category,
                description,
                latitude,
                longitude,
                distanceMeters,
                importanceScore
        );
    }
}
