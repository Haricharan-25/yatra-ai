package com.yatraai.provider.geocoding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yatraai.model.LocationPoint;
import com.yatraai.model.LocationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class NominatimGeocodingProvider implements GeocodingProvider {

    private static final Logger logger = LoggerFactory.getLogger(NominatimGeocodingProvider.class);
    private static final Pattern COORD_PATTERN = Pattern.compile("^\\s*([+-]?\\d+(?:\\.\\d+)?)\\s*[,\\s]\\s*([+-]?\\d+(?:\\.\\d+)?)\\s*$");

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${geocoding.nominatim.url:https://nominatim.openstreetmap.org/reverse}")
    private String nominatimUrl;

    @Value("${geocoding.nominatim.search-url:https://nominatim.openstreetmap.org/search}")
    private String nominatimSearchUrl;

    @Value("${geocoding.nominatim.user-agent:YatraAI-TravelCompanion/1.0 (contact@yatraai.local)}")
    private String userAgent;

    @Value("${geocoding.cache-enabled:true}")
    private boolean cacheEnabled;

    // In-memory cache keyed by quantized coordinate bucket (~100m grid) to respect OSM usage policy
    private final Map<String, LocationResponse> geocodeCache = new ConcurrentHashMap<>();
    private final Map<String, LocationPoint> searchCache = new ConcurrentHashMap<>();

    public NominatimGeocodingProvider(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public LocationResponse reverseGeocode(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) {
            logger.warn("Reverse geocode requested with null coordinates");
            return new LocationResponse(null, null, "Unknown Location", "", "", "Coordinates not provided");
        }

        // Cache lookup
        String cacheKey = toCacheKey(latitude, longitude);
        if (cacheEnabled && geocodeCache.containsKey(cacheKey)) {
            logger.debug("Returning cached reverse geocode for key {}", cacheKey);
            LocationResponse cached = geocodeCache.get(cacheKey);
            return new LocationResponse(latitude, longitude, cached.city(), cached.state(), cached.country(), cached.displayName());
        }

        try {
            String url = UriComponentsBuilder.fromHttpUrl(nominatimUrl)
                    .queryParam("format", "jsonv2")
                    .queryParam("lat", String.format(Locale.US, "%.6f", latitude))
                    .queryParam("lon", String.format(Locale.US, "%.6f", longitude))
                    .queryParam("addressdetails", 1)
                    .toUriString();

            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.USER_AGENT, userAgent);
            headers.set("Accept", "application/json");

            HttpEntity<Void> entity = new HttpEntity<>(headers);

            logger.info("NominatimGeocodingProvider: Requesting lat={}, lon={}", latitude, longitude);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                LocationResponse loc = parseNominatimResponse(response.getBody(), latitude, longitude);
                if (cacheEnabled) {
                    geocodeCache.put(cacheKey, loc);
                }
                return loc;
            } else {
                logger.warn("Nominatim returned status {}", response.getStatusCode());
            }
        } catch (RestClientException ex) {
            logger.error("Error communicating with Nominatim service: {}", ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error during reverse geocoding: {}", ex.getMessage(), ex);
        }

        return createFallbackResponse(latitude, longitude);
    }

    @Override
    public LocationPoint searchDestination(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }

        String cleaned = query.trim();

        // 1. Direct coordinates parsing: "17.385, 78.486" or "17.385 78.486"
        Matcher matcher = COORD_PATTERN.matcher(cleaned);
        if (matcher.matches()) {
            try {
                double lat = Double.parseDouble(matcher.group(1));
                double lon = Double.parseDouble(matcher.group(2));
                if (lat >= -90.0 && lat <= 90.0 && lon >= -180.0 && lon <= 180.0) {
                    return new LocationPoint(lat, lon);
                }
            } catch (NumberFormatException ignored) {}
        }

        String normalizedKey = cleaned.toLowerCase(Locale.ROOT);
        if (cacheEnabled && searchCache.containsKey(normalizedKey)) {
            return searchCache.get(normalizedKey);
        }

        try {
            String url = UriComponentsBuilder.fromHttpUrl(nominatimSearchUrl)
                    .queryParam("q", cleaned)
                    .queryParam("format", "jsonv2")
                    .queryParam("limit", 1)
                    .toUriString();

            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.USER_AGENT, userAgent);
            headers.set("Accept", "application/json");

            HttpEntity<Void> entity = new HttpEntity<>(headers);

            logger.info("NominatimGeocodingProvider: Searching destination for '{}'", cleaned);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                if (root.isArray() && root.size() > 0) {
                    JsonNode first = root.get(0);
                    if (first.hasNonNull("lat") && first.hasNonNull("lon")) {
                        double lat = Double.parseDouble(first.get("lat").asText());
                        double lon = Double.parseDouble(first.get("lon").asText());
                        LocationPoint point = new LocationPoint(lat, lon);
                        if (cacheEnabled) {
                            searchCache.put(normalizedKey, point);
                        }
                        return point;
                    }
                }
            }
        } catch (Exception ex) {
            logger.warn("Destination search failed for '{}': {}", cleaned, ex.getMessage());
        }

        return null;
    }

    private LocationResponse parseNominatimResponse(String jsonBody, double latitude, double longitude) {
        try {
            JsonNode root = objectMapper.readTree(jsonBody);
            JsonNode address = root.path("address");
            String displayName = root.path("display_name").asText("");

            if (root.has("error")) {
                logger.warn("Nominatim response error: {}", root.path("error").asText());
                return createFallbackResponse(latitude, longitude);
            }

            // Fallback hierarchy for city / locality extraction
            String detectedCity = null;
            String[] cityCandidates = {
                    "city",
                    "town",
                    "village",
                    "municipality",
                    "suburb",
                    "district",
                    "county",
                    "state_district",
                    "hamlet",
                    "neighbourhood",
                    "city_district"
            };

            for (String field : cityCandidates) {
                if (address.hasNonNull(field) && !address.get(field).asText().isBlank()) {
                    detectedCity = address.get(field).asText().trim();
                    break;
                }
            }

            if (detectedCity == null || detectedCity.isBlank()) {
                if (address.hasNonNull("state") && !address.path("state").asText().isBlank()) {
                    detectedCity = address.path("state").asText().trim();
                } else {
                    detectedCity = "Unknown Region";
                }
            }

            String state = address.path("state").asText("").trim();
            String country = address.path("country").asText("").trim();

            if (displayName.isBlank()) {
                displayName = buildDisplayName(detectedCity, state, country);
            }

            return new LocationResponse(latitude, longitude, detectedCity, state, country, displayName);
        } catch (Exception e) {
            logger.error("Failed to parse Nominatim JSON response: {}", e.getMessage());
            return createFallbackResponse(latitude, longitude);
        }
    }

    private LocationResponse createFallbackResponse(double latitude, double longitude) {
        String display = String.format(Locale.US, "Location (%.4f, %.4f)", latitude, longitude);
        return new LocationResponse(latitude, longitude, "Unknown Location", "", "", display);
    }

    private String buildDisplayName(String city, String state, String country) {
        StringBuilder sb = new StringBuilder(city);
        if (!state.isBlank()) {
            sb.append(", ").append(state);
        }
        if (!country.isBlank()) {
            sb.append(", ").append(country);
        }
        return sb.toString();
    }

    private String toCacheKey(double latitude, double longitude) {
        // Quantize coordinates to ~100m grid (3 decimal places)
        return String.format(Locale.US, "%.3f,%.3f", latitude, longitude);
    }

    public void clearCache() {
        geocodeCache.clear();
        searchCache.clear();
    }
}
