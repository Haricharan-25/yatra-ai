package com.yatraai.provider.weather;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yatraai.model.Weather;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class OpenMeteoWeatherProvider implements WeatherProvider {

    private static final Logger logger = LoggerFactory.getLogger(OpenMeteoWeatherProvider.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${weather.openmeteo.url:https://api.open-meteo.com/v1/forecast}")
    private String openMeteoUrl;

    @Value("${weather.enabled:true}")
    private boolean weatherEnabled;

    @Value("${weather.cache-duration-minutes:15}")
    private long cacheDurationMinutes;

    private record CachedWeatherEntry(Weather weather, Instant timestamp) {}

    // In-memory weather cache (keyed by ~5km grid)
    private final Map<String, CachedWeatherEntry> weatherCache = new ConcurrentHashMap<>();

    public OpenMeteoWeatherProvider(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Weather getCurrentWeather(double latitude, double longitude) {
        if (!weatherEnabled) {
            logger.debug("Weather provider is disabled by configuration");
            return null;
        }

        String cacheKey = toCacheKey(latitude, longitude);
        CachedWeatherEntry cached = weatherCache.get(cacheKey);
        if (cached != null) {
            if (Instant.now().minusSeconds(cacheDurationMinutes * 60).isBefore(cached.timestamp())) {
                logger.debug("Returning cached weather for {}", cacheKey);
                return cached.weather();
            } else {
                weatherCache.remove(cacheKey);
            }
        }

        try {
            String url = UriComponentsBuilder.fromHttpUrl(openMeteoUrl)
                    .queryParam("latitude", String.format(Locale.US, "%.4f", latitude))
                    .queryParam("longitude", String.format(Locale.US, "%.4f", longitude))
                    .queryParam("current", "temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m")
                    .toUriString();

            logger.info("OpenMeteoWeatherProvider: Fetching weather for {}, {}", latitude, longitude);
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode current = root.path("current");

                if (!current.isMissingNode()) {
                    double temp = current.path("temperature_2m").asDouble(25.0);
                    int humidity = current.path("relative_humidity_2m").asInt(50);
                    int code = current.path("weather_code").asInt(0);
                    double windSpeed = current.path("wind_speed_10m").asDouble(10.0);

                    String condition = mapWeatherCodeToCondition(code);
                    Weather weather = new Weather(temp, condition, humidity, windSpeed);
                    weatherCache.put(cacheKey, new CachedWeatherEntry(weather, Instant.now()));
                    return weather;
                }
            }
        } catch (RestClientException ex) {
            logger.warn("Weather API request failed or timed out: {}. Continuing without weather.", ex.getMessage());
        } catch (Exception ex) {
            logger.warn("Unexpected error fetching weather: {}. Continuing gracefully.", ex.getMessage());
        }

        return null;
    }

    private String mapWeatherCodeToCondition(int code) {
        return switch (code) {
            case 0 -> "Clear sky";
            case 1, 2 -> "Partly cloudy";
            case 3 -> "Overcast";
            case 45, 48 -> "Foggy";
            case 51, 53, 55 -> "Light drizzle";
            case 61, 63, 65 -> "Rain";
            case 71, 73, 75 -> "Snow";
            case 80, 81, 82 -> "Rain showers";
            case 95, 96, 99 -> "Thunderstorm";
            default -> "Pleasant";
        };
    }

    private String toCacheKey(double latitude, double longitude) {
        // ~5km grid bucket
        return String.format(Locale.US, "%.2f,%.2f", latitude, longitude);
    }

    public void clearCache() {
        weatherCache.clear();
    }

    public void setWeatherEnabled(boolean enabled) {
        this.weatherEnabled = enabled;
    }
}
