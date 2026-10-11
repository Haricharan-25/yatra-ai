package com.yatraai;

import com.yatraai.model.LocationResponse;
import com.yatraai.service.GeocodingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class LocationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GeocodingService geocodingService;

    @Test
    @DisplayName("POST /api/location/reverse-geocode with valid coordinates returns 200 with all fields")
    void testValidCoordinates() throws Exception {
        when(geocodingService.reverseGeocode(anyDouble(), anyDouble()))
                .thenReturn(new LocationResponse(17.385044, 78.486671, "Hyderabad", "Telangana", "India", "Hyderabad, Telangana, India"));

        String requestBody = """
                {
                    "latitude": 17.385044,
                    "longitude": 78.486671
                }
                """;

        mockMvc.perform(post("/api/location/reverse-geocode")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latitude").value(17.385044))
                .andExpect(jsonPath("$.longitude").value(78.486671))
                .andExpect(jsonPath("$.city").value("Hyderabad"))
                .andExpect(jsonPath("$.state").value("Telangana"))
                .andExpect(jsonPath("$.country").value("India"))
                .andExpect(jsonPath("$.displayName").value("Hyderabad, Telangana, India"));
    }

    @Test
    @DisplayName("POST /api/location/reverse-geocode with invalid latitude returns 400 Bad Request")
    void testInvalidLatitude() throws Exception {
        String invalidLatitudeBody = """
                {
                    "latitude": 95.0,
                    "longitude": 78.486671
                }
                """;

        mockMvc.perform(post("/api/location/reverse-geocode")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidLatitudeBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("POST /api/location/reverse-geocode with invalid longitude returns 400 Bad Request")
    void testInvalidLongitude() throws Exception {
        String invalidLonBody = """
                {
                    "latitude": 17.385044,
                    "longitude": 195.0
                }
                """;

        mockMvc.perform(post("/api/location/reverse-geocode")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidLonBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }
}
