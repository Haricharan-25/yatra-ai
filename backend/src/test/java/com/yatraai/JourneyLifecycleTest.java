package com.yatraai;

import com.yatraai.model.JourneyLocationRequest;
import com.yatraai.model.JourneyLocationResponse;
import com.yatraai.model.JourneyState;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JourneyLifecycleTest {

    private JourneyService journeyService;
    private StoryTriggerService storyTriggerService;

    @BeforeEach
    void setUp() {
        GeocodingProvider mockGeocoding = (lat, lon) ->
                new LocationResponse(lat, lon, "Hyderabad", "Telangana", "India", "Hyderabad, Telangana");

        PlacesProvider mockPlaces = (lat, lon, r) -> List.of(
                new Place("1", "Golconda Fort", "fort_palace", "Fortress", lat, lon, 500.0, 10)
        );

        WeatherProvider mockWeather = (lat, lon) ->
                new com.yatraai.model.Weather(26.0, "Clear sky", 40, 10.0);

        GeocodingService geocodingService = new GeocodingService(mockGeocoding);
        PlacesService placesService = new PlacesService(mockPlaces);
        WeatherService weatherService = new WeatherService(mockWeather);
        storyTriggerService = new StoryTriggerService();
        storyTriggerService.resetJourney();

        StoryService mockStoryService = new StoryService(null) {
            @Override
            public StoryResponse generateContextualStory(String city, String state, String country,
                                                         Place topPlace, com.yatraai.model.Weather weather,
                                                         List<String> previous, com.yatraai.model.JourneyEvent reason) {
                return new StoryResponse(city, "Story text about " + (topPlace != null ? topPlace.name() : city), 25);
            }
        };

        journeyService = new JourneyService(
                geocodingService,
                placesService,
                weatherService,
                storyTriggerService,
                mockStoryService
        );
    }

    @Test
    @DisplayName("Journey lifecycle: Start sets state to ACTIVE, Pause stops triggers, Resume re-activates, Stop halts")
    void testFullJourneyLifecycle() {
        String journeyId = "test-session-1";

        // 1. Start journey
        JourneyState startState = journeyService.startJourney(journeyId);
        assertEquals(JourneyState.Status.ACTIVE, startState.getStatus());
        assertTrue(startState.isActive());

        // 2. Active location ping triggers story
        JourneyLocationRequest activeRequest = new JourneyLocationRequest(journeyId, 17.3850, 78.4866, Instant.now(), true);
        JourneyLocationResponse activeResponse = journeyService.processLocation(activeRequest);
        assertTrue(activeResponse.triggerStory(), "Active journey should trigger initial story");
        assertNotNull(activeResponse.story());

        // 3. Pause journey
        JourneyState pauseState = journeyService.pauseJourney(journeyId);
        assertEquals(JourneyState.Status.PAUSED, pauseState.getStatus());
        assertTrue(pauseState.isPaused());

        // 4. Paused location ping must NOT trigger story
        JourneyLocationRequest pausedRequest = new JourneyLocationRequest(journeyId, 17.3900, 78.4900, Instant.now().plusSeconds(600), true);
        JourneyLocationResponse pausedResponse = journeyService.processLocation(pausedRequest);
        assertFalse(pausedResponse.triggerStory(), "Paused journey must not trigger stories");
        assertNull(pausedResponse.story());

        // 5. Resume journey
        JourneyState resumeState = journeyService.resumeJourney(journeyId);
        assertEquals(JourneyState.Status.ACTIVE, resumeState.getStatus());
        assertTrue(resumeState.isActive());

        // 6. Stop journey
        JourneyState stopState = journeyService.stopJourney(journeyId);
        assertEquals(JourneyState.Status.STOPPED, stopState.getStatus());
        assertTrue(stopState.isStopped());

        // 7. Stopped location ping must NOT trigger story
        JourneyLocationResponse stoppedResponse = journeyService.processLocation(pausedRequest);
        assertFalse(stoppedResponse.triggerStory(), "Stopped journey must not trigger stories");
        assertNull(stoppedResponse.story());
    }
}
