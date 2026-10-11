package com.yatraai.provider.places;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yatraai.model.Place;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class OverpassPlacesProvider implements PlacesProvider {

    private static final Logger logger = LoggerFactory.getLogger(OverpassPlacesProvider.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${places.overpass.url:https://overpass-api.de/api/interpreter}")
    private String overpassUrl;

    @Value("${places.cache-enabled:true}")
    private boolean cacheEnabled;

    // Cache places by geo-grid (~500m grid bucket)
    private final Map<String, List<Place>> placesCache = new ConcurrentHashMap<>();

    public OverpassPlacesProvider(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<Place> findNearbyPlaces(double latitude, double longitude, double radiusMeters) {
        int radius = (int) Math.max(500, Math.min(radiusMeters, 5000));
        String cacheKey = toCacheKey(latitude, longitude, radius);

        if (cacheEnabled && placesCache.containsKey(cacheKey)) {
            logger.debug("Returning cached places for {}", cacheKey);
            return placesCache.get(cacheKey);
        }

        // Query Overpass for historic landmarks, forts, palaces, archaeological sites,
        // monuments, museums, temples/places of worship, natural features (peaks, rivers, lakes)
        String overpassQuery = String.format(Locale.US,
                "[out:json][timeout:8];" +
                "(" +
                "  nwr[\"historic\"](around:%d,%.6f,%.6f);" +
                "  nwr[\"tourism\"~\"attraction|museum|viewpoint|gallery|theme_park\"](around:%d,%.6f,%.6f);" +
                "  nwr[\"amenity\"=\"place_of_worship\"][\"historic\"](around:%d,%.6f,%.6f);" +
                "  nwr[\"natural\"~\"peak|water|volcano|cave_entrance|beach|cliff|spring\"](around:%d,%.6f,%.6f);" +
                "  nwr[\"water\"~\"river|lake|reservoir\"](around:%d,%.6f,%.6f);" +
                ");" +
                "out center 25;",
                radius, latitude, longitude,
                radius, latitude, longitude,
                radius, latitude, longitude,
                radius, latitude, longitude,
                radius, latitude, longitude
        );

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.set("User-Agent", "YatraAI-TravelCompanion/1.0 (contact@yatraai.local)");

            HttpEntity<String> entity = new HttpEntity<>("data=" + overpassQuery, headers);

            logger.info("OverpassPlacesProvider: Querying places within {}m of {}, {}", radius, latitude, longitude);
            ResponseEntity<String> response = restTemplate.postForEntity(overpassUrl, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<Place> results = parseOverpassResponse(response.getBody(), latitude, longitude);
                if (cacheEnabled) {
                    placesCache.put(cacheKey, results);
                }
                return results;
            }
        } catch (RestClientException ex) {
            logger.warn("Overpass API request failed or timed out: {}. Continuing without remote POIs.", ex.getMessage());
        } catch (Exception ex) {
            logger.warn("Unexpected error querying Overpass places: {}. Continuing gracefully.", ex.getMessage());
        }

        return Collections.emptyList();
    }

    private List<Place> parseOverpassResponse(String jsonBody, double userLat, double userLon) {
        List<Place> places = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(jsonBody);
            JsonNode elements = root.path("elements");

            if (!elements.isArray()) {
                return places;
            }

            for (JsonNode elem : elements) {
                JsonNode tags = elem.path("tags");
                if (tags.isMissingNode() || !tags.hasNonNull("name")) {
                    continue; // Skip elements without recognizable names
                }

                String name = tags.path("name").asText("").trim();
                if (name.isBlank()) {
                    continue;
                }

                // Determine coordinates (node has lat/lon; way/relation has center.lat/center.lon)
                double placeLat = elem.hasNonNull("lat")
                        ? elem.path("lat").asDouble()
                        : elem.path("center").path("lat").asDouble(userLat);

                double placeLon = elem.hasNonNull("lon")
                        ? elem.path("lon").asDouble()
                        : elem.path("center").path("lon").asDouble(userLon);

                String id = elem.path("type").asText("node") + "_" + elem.path("id").asText();
                String category = categorizeElement(tags);
                String description = tags.path("description").asText("");

                places.add(new Place(
                        id,
                        name,
                        category,
                        description,
                        placeLat,
                        placeLon,
                        0.0, // calculated in PlacesService
                        0   // calculated in PlacesService
                ));
            }
        } catch (Exception e) {
            logger.warn("Failed to parse Overpass response: {}", e.getMessage());
        }

        return places;
    }

    private String categorizeElement(JsonNode tags) {
        if (tags.hasNonNull("historic")) {
            String hist = tags.path("historic").asText().toLowerCase();
            if (hist.contains("castle") || hist.contains("fort") || hist.contains("palace")) {
                return "fort_palace";
            }
            if (hist.contains("archaeological") || hist.contains("ruins")) {
                return "archaeological_site";
            }
            if (hist.contains("monument") || hist.contains("memorial")) {
                return "monument";
            }
            return "historical_landmark";
        }

        if (tags.hasNonNull("tourism")) {
            String tour = tags.path("tourism").asText().toLowerCase();
            if (tour.contains("museum") || tour.contains("gallery")) {
                return "museum";
            }
            if (tour.contains("viewpoint")) {
                return "viewpoint";
            }
            return "attraction";
        }

        if (tags.hasNonNull("amenity") && tags.path("amenity").asText().equalsIgnoreCase("place_of_worship")) {
            return "cultural_temple";
        }

        if (tags.hasNonNull("natural") || tags.hasNonNull("water")) {
            return "natural_attraction";
        }

        return "landmark";
    }

    private String toCacheKey(double latitude, double longitude, int radius) {
        // ~500m grid bucket (2 decimal places)
        return String.format(Locale.US, "%.2f,%.2f,%d", latitude, longitude, radius);
    }

    public void clearCache() {
        placesCache.clear();
    }
}
