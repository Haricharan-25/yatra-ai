package com.yatraai.provider.routing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yatraai.model.RouteInfo;
import com.yatraai.model.RoutePoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class OsrmRoutingProvider implements RoutingProvider {

    private static final Logger logger = LoggerFactory.getLogger(OsrmRoutingProvider.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${routing.osrm.url:https://router.project-osrm.org/route/v1/driving}")
    private String osrmUrl;

    @Value("${routing.cache-enabled:true}")
    private boolean cacheEnabled;

    @Value("${geocoding.nominatim.user-agent:YatraAI-TravelCompanion/1.0 (contact@yatraai.local)}")
    private String userAgent;

    // Cache routes to avoid excessive external requests
    private final Map<String, RouteInfo> routeCache = new ConcurrentHashMap<>();

    public OsrmRoutingProvider(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public RouteInfo calculateRoute(double startLat, double startLon, double destLat, double destLon, String destinationName) {
        String cacheKey = toCacheKey(startLat, startLon, destLat, destLon);
        if (cacheEnabled && routeCache.containsKey(cacheKey)) {
            logger.debug("Returning cached route for {}", cacheKey);
            return routeCache.get(cacheKey);
        }

        String requestUrl = String.format(Locale.US,
                "%s/%.6f,%.6f;%.6f,%.6f?overview=full&geometries=geojson",
                osrmUrl, startLon, startLat, destLon, destLat);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", userAgent);
            headers.set("Accept", "application/json");
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            logger.info("OsrmRoutingProvider: Requesting route from {},{} to {},{}",
                    startLat, startLon, destLat, destLon);

            ResponseEntity<String> response = restTemplate.exchange(requestUrl, HttpMethod.GET, entity, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                RouteInfo parsed = parseOsrmResponse(response.getBody(), destLat, destLon, destinationName);
                if (parsed != null) {
                    if (cacheEnabled) {
                        routeCache.put(cacheKey, parsed);
                    }
                    return parsed;
                }
            }
        } catch (Exception ex) {
            logger.warn("OSRM routing request failed: {}. Providing fallback straight-line route.", ex.getMessage());
        }

        // Graceful fallback route: 2-point direct line
        RouteInfo fallback = createFallbackRoute(startLat, startLon, destLat, destLon, destinationName);
        if (cacheEnabled) {
            routeCache.put(cacheKey, fallback);
        }
        return fallback;
    }

    private RouteInfo parseOsrmResponse(String jsonBody, double destLat, double destLon, String destinationName) {
        try {
            JsonNode root = objectMapper.readTree(jsonBody);
            String code = root.path("code").asText("");
            if (!"Ok".equalsIgnoreCase(code)) {
                logger.warn("OSRM returned status code: {}", code);
                return null;
            }

            JsonNode routes = root.path("routes");
            if (!routes.isArray() || routes.isEmpty()) {
                return null;
            }

            JsonNode primaryRoute = routes.get(0);
            double totalDistanceMeters = primaryRoute.path("distance").asDouble(0.0);
            JsonNode coordsNode = primaryRoute.path("geometry").path("coordinates");

            List<RoutePoint> geometry = new ArrayList<>();
            if (coordsNode.isArray()) {
                for (JsonNode coord : coordsNode) {
                    if (coord.isArray() && coord.size() >= 2) {
                        double lon = coord.get(0).asDouble();
                        double lat = coord.get(1).asDouble();
                        geometry.add(new RoutePoint(lat, lon));
                    }
                }
            }

            if (geometry.isEmpty()) {
                return null;
            }

            String summary = String.format(Locale.US, "%.1f km drive", totalDistanceMeters / 1000.0);

            return new RouteInfo(
                    destinationName != null && !destinationName.isBlank() ? destinationName : "Destination",
                    destLat,
                    destLon,
                    totalDistanceMeters,
                    totalDistanceMeters,
                    0.0,
                    geometry,
                    null,
                    null,
                    null,
                    summary
            );
        } catch (Exception ex) {
            logger.error("Error parsing OSRM response: {}", ex.getMessage());
            return null;
        }
    }

    private RouteInfo createFallbackRoute(double startLat, double startLon, double destLat, double destLon, String destinationName) {
        double dist = calculateHaversineDistance(startLat, startLon, destLat, destLon);
        List<RoutePoint> geometry = List.of(
                new RoutePoint(startLat, startLon),
                new RoutePoint(destLat, destLon)
        );

        return new RouteInfo(
                destinationName != null && !destinationName.isBlank() ? destinationName : "Destination",
                destLat,
                destLon,
                dist,
                dist,
                0.0,
                geometry,
                null,
                null,
                null,
                String.format(Locale.US, "Direct ~%.1f km path", dist / 1000.0)
        );
    }

    private double calculateHaversineDistance(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private String toCacheKey(double startLat, double startLon, double destLat, double destLon) {
        // ~200m bucket
        return String.format(Locale.US, "%.2f,%.2f->%.2f,%.2f", startLat, startLon, destLat, destLon);
    }

    public void clearCache() {
        routeCache.clear();
    }
}
