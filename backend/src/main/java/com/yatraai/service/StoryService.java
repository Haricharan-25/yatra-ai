package com.yatraai.service;

import com.yatraai.model.JourneyEvent;
import com.yatraai.model.Place;
import com.yatraai.model.StoryRequest;
import com.yatraai.model.StoryResponse;
import com.yatraai.model.Weather;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StoryService {

    private static final Logger logger = LoggerFactory.getLogger(StoryService.class);

    private final AiService aiService;

    public StoryService(AiService aiService) {
        this.aiService = aiService;
    }

    /**
     * Context-aware story generation endpoint supporting both Phase 1 and Phase 2 requests.
     */
    public StoryResponse generateStory(StoryRequest request) {
        String city = request.city() != null ? request.city().trim() : "Unknown Location";
        String state = request.state() != null ? request.state().trim() : "";
        String country = request.country() != null ? request.country().trim() : "";

        logger.info("Generating travel story for location: {}, {}, {}", city, state, country);

        String storyText;
        if (request.targetPlace() != null || request.weather() != null || (request.previousStories() != null && !request.previousStories().isEmpty())) {
            StoryResponse contextualResp = generateContextualStory(
                    city,
                    state,
                    country,
                    request.targetPlace(),
                    request.weather(),
                    request.previousStories() != null ? request.previousStories() : List.of(),
                    request.triggerReason()
            );
            return contextualResp;
        } else {
            storyText = (aiService != null)
                    ? aiService.generateTravelStory(city, state, country)
                    : "Travel story for " + city;
        }

        int durationSeconds = estimateSpeechDurationSeconds(storyText);
        String placeName = request.targetPlace() != null ? request.targetPlace().name() : null;
        String category = request.targetPlace() != null ? request.targetPlace().category() : null;

        return new StoryResponse(city, storyText, durationSeconds, placeName, category, request.triggerReason());
    }

    /**
     * Contextual story generation for Phase 2 continuous journey (6-arg signature).
     */
    public StoryResponse generateContextualStory(
            String city,
            String state,
            String country,
            Place topPlace,
            Weather weather,
            List<String> previousStories
    ) {
        return generateContextualStory(city, state, country, topPlace, weather, previousStories, null);
    }

    /**
     * Contextual story generation for Phase 2 continuous journey with trigger reason (7-arg signature).
     */
    public StoryResponse generateContextualStory(
            String city,
            String state,
            String country,
            Place topPlace,
            Weather weather,
            List<String> previousStories,
            JourneyEvent triggerReason
    ) {
        logger.info("Generating contextual story for city={}, place={}, weather={}, reason={}",
                city, topPlace != null ? topPlace.name() : "none", weather != null ? weather.condition() : "none", triggerReason);

        String storyText = (aiService != null)
                ? aiService.generateContextualStory(city, state, country, topPlace, weather, previousStories)
                : "Travel story for " + city;
        int durationSeconds = estimateSpeechDurationSeconds(storyText);
        String placeName = topPlace != null ? topPlace.name() : null;
        String category = topPlace != null ? topPlace.category() : null;

        return new StoryResponse(city, storyText, durationSeconds, placeName, category, triggerReason);
    }

    /**
     * Contextual story generation with route destination context (8-arg signature).
     */
    public StoryResponse generateContextualStory(
            String city,
            String state,
            String country,
            Place topPlace,
            Weather weather,
            List<String> previousStories,
            JourneyEvent triggerReason,
            String destination
    ) {
        if (destination == null || destination.isBlank()) {
            return generateContextualStory(city, state, country, topPlace, weather, previousStories, triggerReason);
        }

        logger.info("Generating route-contextual story for city={}, place={}, weather={}, reason={}, destination={}",
                city, topPlace != null ? topPlace.name() : "none", weather != null ? weather.condition() : "none",
                triggerReason, destination);

        String storyText = (aiService != null)
                ? aiService.generateContextualStory(city, state, country, topPlace, weather, previousStories, triggerReason, destination)
                : "Travel story for " + city;
        int durationSeconds = estimateSpeechDurationSeconds(storyText);
        String placeName = topPlace != null ? topPlace.name() : null;
        String category = topPlace != null ? topPlace.category() : null;

        return new StoryResponse(city, storyText, durationSeconds, placeName, category, triggerReason);
    }

    /**
     * Estimates spoken narration time in seconds based on typical 130 words-per-minute TTS pace.
     */
    public int estimateSpeechDurationSeconds(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        String[] words = text.trim().split("\\s+");
        // 130 WPM ~= 2.16 words per second
        return Math.max(15, Math.min(60, (int) Math.round(words.length / 2.16)));
    }
}
