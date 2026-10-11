package com.yatraai.service;

import com.yatraai.model.Place;
import com.yatraai.provider.places.PlacesProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class PlacesService {

    private static final Logger logger = LoggerFactory.getLogger(PlacesService.class);

    private static final double EARTH_RADIUS_METERS = 6371000.0;

    // Configurable trigger distances with explicit field initializers for non-Spring unit tests
    @Value("${places.trigger-distance.major-landmark:2000.0}")
    private double majorLandmarkTriggerDistance = 2000.0;

    @Value("${places.trigger-distance.normal-attraction:1000.0}")
    private double normalAttractionTriggerDistance = 1000.0;

    @Value("${places.trigger-distance.scenic-feature:1500.0}")
    private double scenicFeatureTriggerDistance = 1500.0;

    @Value("${places.min-importance-threshold:6}")
    private int minImportanceThreshold = 6;

    private final PlacesProvider placesProvider;

    public PlacesService(PlacesProvider placesProvider) {
        this.placesProvider = placesProvider;
    }

    /**
     * Finds interesting nearby places, computes Haversine distances, assigns importance scores,
     * filters out low-value locations, and sorts by priority.
     */
    public List<Place> getNearbyPlaces(double userLat, double userLon, double searchRadiusMeters) {
        List<Place> rawPlaces = placesProvider.findNearbyPlaces(userLat, userLon, searchRadiusMeters);
        List<Place> scoredPlaces = new ArrayList<>();

        for (Place place : rawPlaces) {
            double distance = calculateHaversineDistance(userLat, userLon, place.latitude(), place.longitude());
            int score = calculateImportanceScore(place);

            // Filter out ordinary businesses, utility amenities, and irrelevant POIs
            if (score >= minImportanceThreshold) {
                scoredPlaces.add(new Place(
                        place.id(),
                        place.name(),
                        place.category(),
                        place.description(),
                        place.latitude(),
                        place.longitude(),
                        distance,
                        score
                ));
            }
        }

        // Sort: highest importance first, then closest distance
        scoredPlaces.sort(Comparator
                .comparingInt(Place::importanceScore).reversed()
                .thenComparingDouble(Place::distanceMeters)
        );

        logger.info("Found {} meaningful POIs (score >= {}) near {}, {}",
                scoredPlaces.size(), minImportanceThreshold, userLat, userLon);

        return scoredPlaces;
    }

    /**
     * Calculates distance between two coordinates in meters using the Haversine formula.
     */
    public double calculateHaversineDistance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_METERS * c;
    }

    /**
     * Assigns importance score based on categories and notable keywords:
     * - major historical landmark / fort / palace / archaeological site = 10
     * - major monument / cultural landmark / temple = 9
     * - natural attraction (river, lake, mountain, beach) = 8
     * - museum / science centre = 7
     * - food/cultural heritage / viewpoint = 6
     * - ordinary business = 2
     * - ATM/petrol station/etc. = 0
     */
    public int calculateImportanceScore(Place place) {
        String name = place.name() != null ? place.name().toLowerCase() : "";
        String category = place.category() != null ? place.category().toLowerCase() : "";

        // Filter out utilities and non-attractions immediately
        if (name.contains("atm") || name.contains("petrol") || name.contains("fuel") ||
                name.contains("parking") || name.contains("toilet") || name.contains("bank") ||
                category.contains("fuel") || category.contains("atm")) {
            return 0;
        }

        if (name.contains("pharmacy") || name.contains("hospital") || name.contains("mechanic") ||
                name.contains("supermarket") || name.contains("grocery") || name.contains("bakery") ||
                name.contains("shop") || name.contains("store") || category.contains("shop")) {
            return 2;
        }

        // Major historical landmarks / forts / palaces / archaeological sites: 10
        if (category.equals("fort_palace") || category.equals("archaeological_site") ||
                name.contains("fort") || name.contains("palace") || name.contains("castle") ||
                name.contains("charminar") || name.contains("taj mahal") || name.contains("qutub") ||
                name.contains("citadel") || name.contains("acropolis") || name.contains("archaeological") ||
                name.contains("ruins")) {
            return 10;
        }

        // Major monuments & historic temples / cultural landmarks: 9
        if (category.equals("monument") || category.equals("cultural_temple") ||
                name.contains("monument") || name.contains("memorial") ||
                name.contains("temple") || name.contains("cathedral") || name.contains("mosque") ||
                name.contains("basilica") || name.contains("ashram") || name.contains("stupa")) {
            return 9;
        }

        // Natural attractions (mountains, lakes, rivers, falls, peaks): 8
        if (category.equals("natural_attraction") ||
                name.contains("lake") || name.contains("river") || name.contains("waterfall") ||
                name.contains("falls") || name.contains("mountain") || name.contains("peak") ||
                name.contains("valley") || name.contains("canyon") || name.contains("beach")) {
            return 8;
        }

        // Museums and galleries: 7
        if (category.equals("museum") || name.contains("museum") || name.contains("gallery") ||
                name.contains("planetarium") || name.contains("science centre")) {
            return 7;
        }

        // Cultural / heritage / viewpoints: 6
        if (category.equals("attraction") || category.equals("historical_landmark") || category.equals("viewpoint") ||
                name.contains("viewpoint") || name.contains("bazaar") || name.contains("market") ||
                name.contains("heritage") || name.contains("botanical garden") || name.contains("zoo")) {
            return 6;
        }

        return 2;
    }

    public double getMajorLandmarkTriggerDistance() {
        return majorLandmarkTriggerDistance;
    }

    public double getNormalAttractionTriggerDistance() {
        return normalAttractionTriggerDistance;
    }

    public double getScenicFeatureTriggerDistance() {
        return scenicFeatureTriggerDistance;
    }

    public int getMinImportanceThreshold() {
        return minImportanceThreshold;
    }
}
