package com.yatraai;

import com.yatraai.model.JourneyEvent;
import com.yatraai.model.JourneyLocationRequest;
import com.yatraai.model.JourneyLocationResponse;
import com.yatraai.model.JourneyState;
import com.yatraai.model.LocationPoint;
import com.yatraai.model.LocationContext;
import com.yatraai.model.Place;
import com.yatraai.model.Weather;
import com.yatraai.service.JourneyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class JourneyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JourneyService journeyService;

    @Test
    @DisplayName("POST /api/journey/location with valid coordinates returns 200 with narration context")
    void testJourneyLocationEndpoint() throws Exception {
        Place charminar = new Place("1", "Charminar", "monument", "Historic monument", 17.36, 78.47, 500.0, 9);
        Weather weather = new Weather(28.0, "Sunny", 45, 12.0);
        LocationContext context = new LocationContext(
                new LocationPoint(17.385044, 78.486671),
                "Hyderabad",
                "Telangana",
                "India",
                "Hyderabad, Telangana, India",
                List.of(charminar),
                charminar,
                weather,
                List.of(),
                0,
                true,
                JourneyEvent.NEARBY_MAJOR_LANDMARK,
                "Approaching Charminar"
        );

        JourneyLocationResponse mockResponse = new JourneyLocationResponse(
                "journey-101",
                "Hyderabad",
                "Telangana",
                "India",
                "Hyderabad, Telangana, India",
                List.of(charminar),
                charminar,
                weather,
                true,
                JourneyEvent.NEARBY_MAJOR_LANDMARK,
                List.of(JourneyEvent.NEARBY_MAJOR_LANDMARK),
                "Approaching Charminar",
                25,
                context
        );

        when(journeyService.processLocation(any(JourneyLocationRequest.class))).thenReturn(mockResponse);

        String requestBody = """
                {
                    "journeyId": "journey-101",
                    "latitude": 17.385044,
                    "longitude": 78.486671,
                    "timestamp": "2026-10-04T13:30:00Z",
                    "autoNarrationEnabled": true
                }
                """;

        mockMvc.perform(post("/api/journey/location")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.journeyId").value("journey-101"))
                .andExpect(jsonPath("$.city").value("Hyderabad"))
                .andExpect(jsonPath("$.nearbyPlaces").isArray())
                .andExpect(jsonPath("$.triggerStory").value(true))
                .andExpect(jsonPath("$.narrationRecommended").value(true))
                .andExpect(jsonPath("$.story").value("Approaching Charminar"));
    }

    @Test
    @DisplayName("POST /api/journey/location with invalid latitude returns 400 Bad Request")
    void testInvalidLatitudeJourney() throws Exception {
        String invalidBody = """
                {
                    "latitude": -120.0,
                    "longitude": 78.486671
                }
                """;

        mockMvc.perform(post("/api/journey/location")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("Journey lifecycle endpoints: start, pause, resume, stop work correctly")
    void testJourneyLifecycleEndpoints() throws Exception {
        JourneyState activeState = new JourneyState("j-1");
        activeState.setStatus(JourneyState.Status.ACTIVE);

        JourneyState pausedState = new JourneyState("j-1");
        pausedState.setStatus(JourneyState.Status.PAUSED);

        JourneyState stoppedState = new JourneyState("j-1");
        stoppedState.setStatus(JourneyState.Status.STOPPED);

        when(journeyService.startJourney("j-1")).thenReturn(activeState);
        when(journeyService.pauseJourney("j-1")).thenReturn(pausedState);
        when(journeyService.resumeJourney("j-1")).thenReturn(activeState);
        when(journeyService.stopJourney("j-1")).thenReturn(stoppedState);

        // Start
        mockMvc.perform(post("/api/journey/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"journeyId\":\"j-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // Pause
        mockMvc.perform(post("/api/journey/pause")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"journeyId\":\"j-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAUSED"));

        // Resume
        mockMvc.perform(post("/api/journey/resume")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"journeyId\":\"j-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // Stop
        mockMvc.perform(post("/api/journey/stop")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"journeyId\":\"j-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("STOPPED"));
    }

    @Test
    @DisplayName("POST /api/journey/reset returns 200 with status message")
    void testJourneyReset() throws Exception {
        mockMvc.perform(post("/api/journey/reset"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Journey reset successfully"));
    }
}
