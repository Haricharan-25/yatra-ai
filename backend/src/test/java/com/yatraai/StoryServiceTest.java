package com.yatraai;

import com.yatraai.model.StoryRequest;
import com.yatraai.model.StoryResponse;
import com.yatraai.service.StoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class StoryServiceTest {

    @Autowired
    private StoryService storyService;

    @Test
    @DisplayName("Story service generates a valid story and duration for a given city")
    void testStoryGenerationForCity() {
        StoryRequest request = new StoryRequest("Hyderabad", "Telangana", "India");
        StoryResponse response = storyService.generateStory(request);

        assertNotNull(response);
        assertEquals("Hyderabad", response.city());
        assertNotNull(response.story());
        assertFalse(response.story().isBlank());
        assertTrue(response.durationSeconds() >= 15);
    }
}
