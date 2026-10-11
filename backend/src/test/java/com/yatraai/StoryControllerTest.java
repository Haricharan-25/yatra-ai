package com.yatraai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class StoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("POST /api/story/generate with valid city returns 200 and story payload")
    void testValidStoryGeneration() throws Exception {
        String requestBody = """
                {
                    "city": "Hyderabad",
                    "state": "Telangana",
                    "country": "India"
                }
                """;

        mockMvc.perform(post("/api/story/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.city").value("Hyderabad"))
                .andExpect(jsonPath("$.story").isString())
                .andExpect(jsonPath("$.durationSeconds").isNumber());
    }

    @Test
    @DisplayName("POST /api/story/generate with POI and triggerReason returns structured response with place metadata")
    void testContextualStoryGeneration() throws Exception {
        String requestBody = """
                {
                    "city": "Hyderabad",
                    "state": "Telangana",
                    "country": "India",
                    "targetPlace": {
                        "id": "fort_1",
                        "name": "Golconda Fort",
                        "category": "fort_palace",
                        "description": "Historic medieval fortress",
                        "latitude": 17.38,
                        "longitude": 78.40,
                        "distanceMeters": 450.0,
                        "importanceScore": 10
                    },
                    "triggerReason": "NEARBY_MAJOR_LANDMARK"
                }
                """;

        mockMvc.perform(post("/api/story/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.city").value("Hyderabad"))
                .andExpect(jsonPath("$.placeName").value("Golconda Fort"))
                .andExpect(jsonPath("$.category").value("fort_palace"))
                .andExpect(jsonPath("$.triggerReason").value("NEARBY_MAJOR_LANDMARK"))
                .andExpect(jsonPath("$.story").isString());
    }

    @Test
    @DisplayName("POST /api/story/generate with blank city returns 400 Bad Request")
    void testBlankCityValidation() throws Exception {
        String invalidBody = """
                {
                    "city": "   ",
                    "state": "Telangana",
                    "country": "India"
                }
                """;

        mockMvc.perform(post("/api/story/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }
}
