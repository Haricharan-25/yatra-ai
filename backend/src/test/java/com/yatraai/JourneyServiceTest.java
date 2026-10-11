package com.yatraai;

import com.yatraai.model.JourneyEvent;
import com.yatraai.model.JourneyLocationRequest;
import com.yatraai.model.JourneyLocationResponse;
import com.yatraai.model.LocationResponse;
import com.yatraai.model.Place;
import com.yatraai.model.StoryResponse;
import com.yatraai.provider.geocoding.GeocodingProvider;
import com.yatraai.provider.places.PlacesProvider;
import com.yatraai.provider.weather.WeatherProvider;
import com.yatraai.service.GeocodingService;
import com.yatraai.service.JourneyService;
import com.yatraai.service.PlacesService;
import com.yatraai.service.StoryService;
import com.yatraai.service.StoryTriggerService;
import com.yatraai.service.WeatherService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JourneyServiceTest {

    @Test
    @DisplayName("JourneyService gracefully handles weather and places provider exceptions")
    void testProviderFailureResilience() {
        // Mock failing weather and places providers
        PlacesProvider failingPlacesProvider = (lat, lon, r) -> {
            throw new RuntimeException("Simulated Overpass API connection timeout");
        };

        WeatherProvider failingWeatherProvider = (lat, lon) -> {
            throw new RuntimeException("Simulated Weather API failure");
        };

        GeocodingProvider mockGeocoding = (lat, lon) ->
                new LocationResponse(lat, lon, "Hyderabad", "Telangana", "India", "Hyderabad, Telangana");

        GeocodingService geocodingService = new GeocodingService(mockGeocoding);
        PlacesService placesService = new PlacesService(failingPlacesProvider);
        WeatherService weatherService = new WeatherService(failingWeatherProvider);
        StoryTriggerService triggerService = new StoryTriggerService();
        triggerService.resetJourney();

        StoryService mockStoryService = new StoryService(null) {
            @Override
            public StoryResponse generateContextualStory(String city, String state, String country,
                                                         Place topPlace, com.yatraai.model.Weather weather,
                                                         List<String> previous, JourneyEvent reason) {
                return new StoryResponse(city, "Graceful fallback story", 25);
            }

            @Override
            public StoryResponse generateContextualStory(String city, String state, String country,
                                                         Place topPlace, com.yatraai.model.Weather weather,
                                                         List<String> previous) {
                return new StoryResponse(city, "Graceful fallback story", 25);
            }
        };

        JourneyService service = new JourneyService(
                geocodingService,
                placesService,
                weatherService,
                triggerService,
                mockStoryService
        );

        JourneyLocationRequest request = new JourneyLocationRequest(17.3850, 78.4866, Instant.now(), true);

        // Must NOT throw exception
        JourneyLocationResponse response = assertDoesNotThrow(() -> service.processLocation(request));

        assertNotNull(response);
        assertEquals("Hyderabad", response.city());
        assertNull(response.weather()); // Continues without weather
        assertTrue(response.nearbyPlaces().isEmpty()); // Continues without POIs
        assertTrue(response.triggerStory()); // City initial entry still triggers story
        assertEquals("Graceful fallback story", response.story());
    }
}
