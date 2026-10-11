package com.yatraai.model;

import java.util.List;

public record RouteInfo(
        String destinationName,
        Double destinationLat,
        Double destinationLon,
        double totalDistanceMeters,
        double remainingDistanceMeters,
        double progressPercent,
        List<RoutePoint> geometry,
        Place nextUpcomingPlace,
        Double distanceToNextPlaceMeters,
        Boolean isNextPlaceUpcoming,
        String summary
) {
    public RouteInfo(
            String destinationName,
            Double destinationLat,
            Double destinationLon,
            double totalDistanceMeters,
            double remainingDistanceMeters,
            double progressPercent,
            List<RoutePoint> geometry
    ) {
        this(
                destinationName,
                destinationLat,
                destinationLon,
                totalDistanceMeters,
                remainingDistanceMeters,
                progressPercent,
                geometry,
                null,
                null,
                null,
                null
        );
    }
}
