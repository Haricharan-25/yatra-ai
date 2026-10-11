package com.yatraai.service;

import com.yatraai.model.JourneyState;
import com.yatraai.model.LocationPoint;
import com.yatraai.model.Place;
import com.yatraai.model.RouteInfo;
import com.yatraai.model.RoutePoint;
import com.yatraai.provider.routing.RoutingProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class RouteService {

    private static final Logger logger = LoggerFactory.getLogger(RouteService.class);
    private static final double EARTH_RADIUS = 6371000.0;

    @Value("${routing.corridor-width-meters:2000.0}")
    private double corridorWidthMeters = 2000.0;

    @Value("${routing.lead-distance.major-landmark:2500.0}")
    private double majorLandmarkLeadDistance = 2500.0;

    @Value("${routing.lead-distance.normal-attraction:1500.0}")
    private double normalAttractionLeadDistance = 1500.0;

    // Heading is considered reliable only if speed is at least 1.5 m/s (~5.4 km/h)
    private static final double MIN_SPEED_FOR_RELIABLE_HEADING = 1.5;

    private final RoutingProvider routingProvider;
    private final GeocodingService geocodingService;

    public RouteService(RoutingProvider routingProvider, GeocodingService geocodingService) {
        this.routingProvider = routingProvider;
        this.geocodingService = geocodingService;
    }

    public record ProjectionResult(
            double crossTrackDistanceMeters,
            double alongTrackDistanceMeters,
            int segmentIndex
    ) {}

    public record RoutePlaceClassification(
            Place place,
            double crossTrackDistanceMeters,
            double distanceAheadOnRouteMeters,
            boolean isUpcoming,
            boolean isPassed,
            boolean isDirectlyOnRouteCorridor
    ) {}

    public record ClassifiedRouteSummary(
            List<Place> upcomingPlaces,
            List<Place> passedPlaces,
            Place nextUpcomingPlace,
            Double distanceToNextPlaceMeters,
            Boolean isNextPlaceUpcoming
    ) {}

    /**
     * Calculates or refreshes a journey route based on destination.
     */
    public RouteInfo setupRoute(
            JourneyState journeyState,
            double currentLat,
            double currentLon,
            String destination,
            Double destLat,
            Double destLon
    ) {
        if (destination == null || destination.isBlank()) {
            return null;
        }

        String targetDest = destination.trim();

        // If coordinates not directly provided, search via Geocoding
        double resolvedLat;
        double resolvedLon;

        if (destLat != null && destLon != null) {
            resolvedLat = destLat;
            resolvedLon = destLon;
        } else {
            LocationPoint point = geocodingService.searchDestination(targetDest);
            if (point == null) {
                logger.warn("Could not geocode destination '{}'. Route planning skipped.", targetDest);
                return null;
            }
            resolvedLat = point.latitude();
            resolvedLon = point.longitude();
        }

        RouteInfo route = null;
        try {
            route = routingProvider.calculateRoute(currentLat, currentLon, resolvedLat, resolvedLon, targetDest);
        } catch (Exception ex) {
            logger.warn("Routing calculation failed for '{}': {}. Falling back to straight-line path.", targetDest, ex.getMessage());
            double dist = calculateHaversineDistance(currentLat, currentLon, resolvedLat, resolvedLon);
            route = new RouteInfo(
                    targetDest,
                    resolvedLat,
                    resolvedLon,
                    dist,
                    dist,
                    0.0,
                    List.of(new RoutePoint(currentLat, currentLon), new RoutePoint(resolvedLat, resolvedLon)),
                    null,
                    null,
                    null,
                    "Direct path fallback"
            );
        }

        if (route != null && journeyState != null) {
            journeyState.setCurrentRoute(route);
        }
        return route;
    }

    /**
     * Updates traveler's progress along the current route.
     */
    public RouteInfo updateRouteProgress(
            RouteInfo route,
            double currentLat,
            double currentLon,
            Double heading,
            Double speed,
            List<Place> candidatePlaces
    ) {
        if (route == null || route.geometry() == null || route.geometry().isEmpty()) {
            return route;
        }

        ProjectionResult userProj = projectPointOnPolyline(currentLat, currentLon, route.geometry());
        double totalDist = route.totalDistanceMeters();
        double userProgress = userProj.alongTrackDistanceMeters();
        double remainingDist = Math.max(0.0, totalDist - userProgress);
        double progressPct = totalDist > 0.0 ? Math.min(100.0, Math.max(0.0, (userProgress / totalDist) * 100.0)) : 0.0;

        ClassifiedRouteSummary classification = classifyPlacesAlongRoute(
                route,
                userProgress,
                currentLat,
                currentLon,
                heading,
                speed,
                candidatePlaces
        );

        String summary = String.format(Locale.US, "%.1f km remaining (%.0f%%)",
                remainingDist / 1000.0, progressPct);

        return new RouteInfo(
                route.destinationName(),
                route.destinationLat(),
                route.destinationLon(),
                totalDist,
                remainingDist,
                progressPct,
                route.geometry(),
                classification.nextUpcomingPlace(),
                classification.distanceToNextPlaceMeters(),
                classification.isNextPlaceUpcoming(),
                summary
        );
    }

    /**
     * Filters and classifies candidate places along the upcoming route corridor.
     */
    public ClassifiedRouteSummary classifyPlacesAlongRoute(
            RouteInfo route,
            double userAlongTrackProgress,
            double userLat,
            double userLon,
            Double heading,
            Double speed,
            List<Place> candidatePlaces
    ) {
        if (route == null || route.geometry() == null || route.geometry().isEmpty() || candidatePlaces == null || candidatePlaces.isEmpty()) {
            return new ClassifiedRouteSummary(List.of(), List.of(), null, null, null);
        }

        List<RoutePlaceClassification> classified = new ArrayList<>();
        boolean headingIsReliable = isHeadingReliable(heading, speed);

        for (Place place : candidatePlaces) {
            if (place == null) continue;

            ProjectionResult placeProj = projectPointOnPolyline(place.latitude(), place.longitude(), route.geometry());
            double crossTrack = placeProj.crossTrackDistanceMeters();

            // Strict corridor filter: only places near the upcoming route road are considered on-route
            boolean inCorridor = crossTrack <= corridorWidthMeters;
            double distanceAhead = placeProj.alongTrackDistanceMeters() - userAlongTrackProgress;

            boolean isPassed = distanceAhead < -250.0; // Behind traveler along route by > 250m
            boolean isUpcoming = inCorridor && distanceAhead >= -50.0;

            // Heading consistency check if heading is reliable and moving fast
            if (isUpcoming && headingIsReliable && distanceAhead > 50.0) {
                double bearing = calculateBearing(userLat, userLon, place.latitude(), place.longitude());
                double angleDiff = Math.abs(normalizeAngle(bearing - heading));
                // If moving at high speed in the opposite direction of the attraction
                if (angleDiff > 100.0 && distanceAhead < 400.0) {
                    isUpcoming = false;
                }
            }

            classified.add(new RoutePlaceClassification(
                    place,
                    crossTrack,
                    distanceAhead,
                    isUpcoming,
                    isPassed,
                    inCorridor
            ));
        }

        List<Place> upcomingPlaces = new ArrayList<>();
        List<Place> passedPlaces = new ArrayList<>();

        for (RoutePlaceClassification c : classified) {
            if (c.isUpcoming()) {
                upcomingPlaces.add(c.place());
            } else if (c.isPassed()) {
                passedPlaces.add(c.place());
            }
        }

        // Sort upcoming places: highest importance first, then closest distance ahead
        upcomingPlaces.sort(Comparator
                .comparingInt(Place::importanceScore).reversed()
                .thenComparingDouble(Place::distanceMeters)
        );

        // Find the next upcoming place along the route
        Place nextPlace = null;
        Double distToNext = null;
        Boolean isUpcoming = null;

        // Earliest upcoming attraction physically ahead on road
        RoutePlaceClassification earliestUpcoming = classified.stream()
                .filter(c -> c.isUpcoming() && c.distanceAheadOnRouteMeters() >= 0)
                .min(Comparator.comparingDouble(RoutePlaceClassification::distanceAheadOnRouteMeters))
                .orElse(null);

        if (earliestUpcoming != null) {
            nextPlace = earliestUpcoming.place();
            distToNext = Math.max(0.0, earliestUpcoming.distanceAheadOnRouteMeters());
            isUpcoming = true;
        } else {
            // Check if there is a recently passed attraction
            RoutePlaceClassification recentlyPassed = classified.stream()
                    .filter(RoutePlaceClassification::isPassed)
                    .max(Comparator.comparingDouble(RoutePlaceClassification::distanceAheadOnRouteMeters))
                    .orElse(null);
            if (recentlyPassed != null) {
                nextPlace = recentlyPassed.place();
                distToNext = Math.abs(recentlyPassed.distanceAheadOnRouteMeters());
                isUpcoming = false;
            }
        }

        return new ClassifiedRouteSummary(
                upcomingPlaces,
                passedPlaces,
                nextPlace,
                distToNext,
                isUpcoming
        );
    }

    /**
     * Determines whether an attraction is eligible for advance narration along the route.
     */
    public boolean isEligibleForAdvanceNarration(Place place, double distanceAheadOnRouteMeters) {
        if (place == null) return false;
        if (distanceAheadOnRouteMeters < -100.0) return false; // Passed

        int score = place.importanceScore();
        String cat = place.category() != null ? place.category().toLowerCase() : "";
        boolean isNatural = cat.contains("natural") || cat.contains("water") || cat.contains("lake");

        if (score >= 9) {
            return distanceAheadOnRouteMeters <= majorLandmarkLeadDistance;
        }
        if (isNatural) {
            return distanceAheadOnRouteMeters <= Math.max(normalAttractionLeadDistance, 1800.0);
        }
        if (score >= 6) {
            return distanceAheadOnRouteMeters <= normalAttractionLeadDistance;
        }

        return false;
    }

    /**
     * Projects a coordinate point onto a polyline to find cross-track and along-track distances.
     */
    public ProjectionResult projectPointOnPolyline(double lat, double lon, List<RoutePoint> polyline) {
        if (polyline == null || polyline.isEmpty()) {
            return new ProjectionResult(0.0, 0.0, 0);
        }

        if (polyline.size() == 1) {
            RoutePoint single = polyline.get(0);
            double dist = calculateHaversineDistance(lat, lon, single.latitude(), single.longitude());
            return new ProjectionResult(dist, 0.0, 0);
        }

        double minDistance = Double.MAX_VALUE;
        double alongTrackAtMin = 0.0;
        int bestSegment = 0;

        double cumulativeDistance = 0.0;

        for (int i = 0; i < polyline.size() - 1; i++) {
            RoutePoint p1 = polyline.get(i);
            RoutePoint p2 = polyline.get(i + 1);

            double segmentLength = calculateHaversineDistance(p1.latitude(), p1.longitude(), p2.latitude(), p2.longitude());
            if (segmentLength < 0.001) {
                continue;
            }

            // Equirectangular projection for local segment
            double midLat = Math.toRadians((p1.latitude() + p2.latitude()) / 2.0);
            double cosMidLat = Math.cos(midLat);

            double x1 = 0.0;
            double y1 = 0.0;

            double x2 = Math.toRadians(p2.longitude() - p1.longitude()) * cosMidLat * EARTH_RADIUS;
            double y2 = Math.toRadians(p2.latitude() - p1.latitude()) * EARTH_RADIUS;

            double px = Math.toRadians(lon - p1.longitude()) * cosMidLat * EARTH_RADIUS;
            double py = Math.toRadians(lat - p1.latitude()) * EARTH_RADIUS;

            double segmentDistSq = x2 * x2 + y2 * y2;
            double t = 0.0;
            if (segmentDistSq > 0.0001) {
                t = (px * x2 + py * y2) / segmentDistSq;
                t = Math.max(0.0, Math.min(1.0, t));
            }

            double closestX = t * x2;
            double closestY = t * y2;

            double dx = px - closestX;
            double dy = py - closestY;
            double distToSegment = Math.sqrt(dx * dx + dy * dy);

            if (distToSegment < minDistance) {
                minDistance = distToSegment;
                alongTrackAtMin = cumulativeDistance + (t * segmentLength);
                bestSegment = i;
            }

            cumulativeDistance += segmentLength;
        }

        return new ProjectionResult(minDistance, alongTrackAtMin, bestSegment);
    }

    public boolean isHeadingReliable(Double heading, Double speed) {
        if (heading == null || heading < 0.0 || heading > 360.0) {
            return false;
        }
        if (speed == null || speed < MIN_SPEED_FOR_RELIABLE_HEADING) {
            return false;
        }
        return true;
    }

    public double calculateBearing(double lat1, double lon1, double lat2, double lon2) {
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double deltaLambda = Math.toRadians(lon2 - lon1);

        double y = Math.sin(deltaLambda) * Math.cos(phi2);
        double x = Math.cos(phi1) * Math.sin(phi2) - Math.sin(phi1) * Math.cos(phi2) * Math.cos(deltaLambda);
        double theta = Math.atan2(y, x);

        return (Math.toDegrees(theta) + 360.0) % 360.0;
    }

    private double normalizeAngle(double angle) {
        while (angle > 180.0) angle -= 360.0;
        while (angle < -180.0) angle += 360.0;
        return angle;
    }

    public double calculateHaversineDistance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS * c;
    }

    public double getCorridorWidthMeters() {
        return corridorWidthMeters;
    }

    public void setCorridorWidthMeters(double corridorWidthMeters) {
        this.corridorWidthMeters = corridorWidthMeters;
    }

    public double getMajorLandmarkLeadDistance() {
        return majorLandmarkLeadDistance;
    }

    public void setMajorLandmarkLeadDistance(double majorLandmarkLeadDistance) {
        this.majorLandmarkLeadDistance = majorLandmarkLeadDistance;
    }

    public double getNormalAttractionLeadDistance() {
        return normalAttractionLeadDistance;
    }

    public void setNormalAttractionLeadDistance(double normalAttractionLeadDistance) {
        this.normalAttractionLeadDistance = normalAttractionLeadDistance;
    }
}
