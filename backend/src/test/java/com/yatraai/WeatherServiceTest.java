package com.yatraai;

import com.yatraai.model.Weather;
import com.yatraai.provider.weather.WeatherProvider;
import com.yatraai.service.WeatherService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WeatherServiceTest {

    @Test
    @DisplayName("WeatherService returns weather object when provider is available")
    void testWeatherSuccess() {
        WeatherProvider mockProvider = (lat, lon) -> new Weather(24.5, "Partly cloudy", 60, 12.0);
        WeatherService weatherService = new WeatherService(mockProvider);

        Weather weather = weatherService.getCurrentWeather(17.3850, 78.4866);
        assertNotNull(weather);
        assertEquals(24.5, weather.temperatureCelsius());
        assertEquals("Partly cloudy", weather.condition());
    }

    @Test
    @DisplayName("WeatherService handles null weather data gracefully")
    void testWeatherNullGraceful() {
        WeatherProvider nullProvider = (lat, lon) -> null;
        WeatherService weatherService = new WeatherService(nullProvider);

        Weather weather = weatherService.getCurrentWeather(17.3850, 78.4866);
        assertNull(weather);
    }

    @Test
    @DisplayName("WeatherService catches provider exceptions and returns null without throwing")
    void testWeatherExceptionHandled() {
        WeatherProvider failingProvider = (lat, lon) -> {
            throw new RuntimeException("Simulated connection timeout to Open-Meteo");
        };
        WeatherService weatherService = new WeatherService(failingProvider);

        Weather weather = assertDoesNotThrow(() -> weatherService.getCurrentWeather(17.3850, 78.4866));
        assertNull(weather);
    }
}
