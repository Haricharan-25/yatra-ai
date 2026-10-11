package com.yatraai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yatraai.model.JourneyEvent;
import com.yatraai.model.Place;
import com.yatraai.model.Weather;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AiService {

    private static final Logger logger = LoggerFactory.getLogger(AiService.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${ai.api.key:}")
    private String apiKey;

    @Value("${ai.api.url:https://api.openai.com/v1/chat/completions}")
    private String apiUrl;

    @Value("${ai.model:gpt-4o-mini}")
    private String modelName;

    public AiService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Backward-compatible Phase 1 method for city-level story generation.
     */
    public String generateTravelStory(String city, String state, String country) {
        return generateContextualStory(city, state, country, null, null, List.of());
    }

    /**
     * Context-aware travel narration generator (6-arg backward-compatible signature).
     */
    public String generateContextualStory(
            String city,
            String state,
            String country,
            Place topPlace,
            Weather weather,
            List<String> previousStories
    ) {
        return generateContextualStory(city, state, country, topPlace, weather, previousStories, null, null);
    }

    /**
     * Route-aware and context-aware travel narration generator.
     */
    public String generateContextualStory(
            String city,
            String state,
            String country,
            Place topPlace,
            Weather weather,
            List<String> previousStories,
            JourneyEvent triggerReason,
            String destination
    ) {
        if (apiKey == null || apiKey.trim().isEmpty() || apiKey.trim().equalsIgnoreCase("none")) {
            logger.info("AI_API_KEY is not configured. Using local development contextual story generator.");
            return generateContextualFallbackStory(city, state, country, topPlace, weather, triggerReason, destination);
        }

        try {
            logger.info("Calling configured LLM API at {} with location context (city={}, poi={}, destination={})",
                    apiUrl, city, topPlace != null ? topPlace.name() : "none", destination);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey.trim());

            String systemPrompt = """
                    You are YatraAI, an intelligent audio travel companion.
                    Generate a short travel narration about the traveler's current journey and upcoming surroundings.
                    Rules:
                    - Approximately 20–40 seconds when spoken (about 50 to 80 words)
                    - Sound warm, engaging, and conversational when read aloud by text-to-speech
                    - If approaching an upcoming attraction along a planned route, introduce it naturally before arrival
                    - If no route is active, introduce nearby landmarks or cultural facts about the region
                    - Do not falsely claim the traveler will pass an attraction if route data does not support it
                    - Mention weather only if it naturally enriches the travel mood
                    - Avoid repeating themes or facts from previously narrated stories
                    - Never use markdown, asterisks, bullet points, hashtags, or section titles
                    - Never say you are an AI or language model
                    - Never invent false historical facts
                    - Output plain text only suitable for audio playback
                    """;

            String userPrompt = buildContextPrompt(city, state, country, topPlace, weather, previousStories, triggerReason, destination);

            Map<String, Object> requestPayload = new HashMap<>();
            requestPayload.put("model", modelName);
            requestPayload.put("temperature", 0.7);
            requestPayload.put("max_tokens", 250);
            requestPayload.put("messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userPrompt)
            ));

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestPayload, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode choices = root.path("choices");
                if (choices.isArray() && !choices.isEmpty()) {
                    String generatedText = choices.get(0).path("message").path("content").asText();
                    if (generatedText != null && !generatedText.isBlank()) {
                        return sanitizeStoryText(generatedText);
                    }
                }
            }

            logger.warn("AI API returned unexpected response. Falling back to local generator.");
        } catch (Exception ex) {
            logger.error("Failed to generate story via AI API ({}). Utilizing fallback generator.", ex.getMessage());
        }

        return generateContextualFallbackStory(city, state, country, topPlace, weather, triggerReason, destination);
    }

    private String buildContextPrompt(
            String city,
            String state,
            String country,
            Place topPlace,
            Weather weather,
            List<String> previousStories,
            JourneyEvent triggerReason,
            String destination
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("Current Location: ").append(city);
        if (state != null && !state.isBlank()) sb.append(", ").append(state);
        if (country != null && !country.isBlank()) sb.append(", ").append(country);
        sb.append(".\n");

        if (destination != null && !destination.isBlank()) {
            sb.append("Travel Route: En route to ").append(destination).append(".\n");
        }

        if (triggerReason == JourneyEvent.ROUTE_UPCOMING_LANDMARK || triggerReason == JourneyEvent.ROUTE_UPCOMING_ATTRACTION) {
            sb.append("Route Event: Traveler is approaching an attraction along the upcoming route.\n");
        }

        if (topPlace != null) {
            sb.append("Target Landmark: ").append(topPlace.name());
            if (topPlace.category() != null) sb.append(" (").append(topPlace.category()).append(")");
            sb.append(String.format(Locale.US, ", approximately %.0f meters ahead along route/travel path.\n", topPlace.distanceMeters()));
            if (topPlace.description() != null && !topPlace.description().isBlank()) {
                sb.append("Landmark Note: ").append(topPlace.description()).append("\n");
            }
        }

        if (weather != null) {
            sb.append(String.format(Locale.US, "Weather: %.1f°C, %s, wind %.1f km/h.\n",
                    weather.temperatureCelsius(), weather.condition(), weather.windSpeedKmh()));
        }

        if (previousStories != null && !previousStories.isEmpty()) {
            sb.append("Recent stories already told on this journey (do not repeat):\n");
            for (String prev : previousStories) {
                sb.append("- ").append(prev.length() > 80 ? prev.substring(0, 80) + "..." : prev).append("\n");
            }
        }

        return sb.toString();
    }

    public String generateContextualFallbackStory(
            String city,
            String state,
            String country,
            Place topPlace,
            Weather weather
    ) {
        return generateContextualFallbackStory(city, state, country, topPlace, weather, null, null);
    }

    public String generateContextualFallbackStory(
            String city,
            String state,
            String country,
            Place topPlace,
            Weather weather,
            JourneyEvent triggerReason,
            String destination
    ) {
        String cleanCity = (city != null && !city.isBlank() && !city.equalsIgnoreCase("Unknown Location"))
                ? city
                : (state != null && !state.isBlank() ? state : "this scenic region");

        // 1. Route-aware Advance Narration
        if ((triggerReason == JourneyEvent.ROUTE_UPCOMING_LANDMARK || triggerReason == JourneyEvent.ROUTE_UPCOMING_ATTRACTION)
                && topPlace != null && topPlace.name() != null && !topPlace.name().isBlank()) {
            String placeName = topPlace.name().trim();
            String cat = topPlace.category() != null ? topPlace.category() : "landmark";
            double dist = topPlace.distanceMeters();
            String distText = dist < 1000
                    ? String.format(Locale.US, "about %.0f meters ahead", dist)
                    : String.format(Locale.US, "about %.1f kilometers ahead", dist / 1000.0);
            String destPhrase = destination != null && !destination.isBlank()
                    ? String.format("en route to %s", destination)
                    : "on your journey";

            if (cat.contains("fort") || cat.contains("palace") || placeName.toLowerCase().contains("fort")) {
                return String.format(
                        "Ahead %s, look out for the historic %s %s. " +
                        "This formidable fortification guarded trade corridors across dynasties, witnessing centuries of royal pageantry. " +
                        "Watch the horizon as its historic ramparts come into view.",
                        destPhrase, placeName, distText
                );
            }

            if (cat.contains("temple") || cat.contains("cultural") || placeName.toLowerCase().contains("temple")) {
                return String.format(
                        "Coming up on your route %s, you will soon pass %s, located %s. " +
                        "Celebrated for its timeless architecture and spiritual heritage, it stands as an enduring emblem of regional culture. " +
                        "Enjoy the view as you cruise along the route.",
                        destPhrase, placeName, distText
                );
            }

            if (cat.contains("natural") || placeName.toLowerCase().contains("lake") || placeName.toLowerCase().contains("river")) {
                return String.format(
                        "Scenic landscapes unfold ahead %s as you approach %s, situated %s. " +
                        "These natural waters create a refreshing pause along the road and support rich local biodiversity. " +
                        "Take in the open vista as you continue onward.",
                        destPhrase, placeName, distText
                );
            }

            return String.format(
                    "Ahead on your route %s, you are approaching %s %s. " +
                    "Known locally as a prominent %s, this destination holds fascinating stories of regional heritage. " +
                    "Keep an eye out as your road takes you past this noteworthy site.",
                    destPhrase, placeName, distText, cat.replace("_", " ")
            );
        }

        // 2. Standard Proximity Landmark Narration
        if (topPlace != null && topPlace.name() != null && !topPlace.name().isBlank()) {
            String placeName = topPlace.name().trim();
            String cat = topPlace.category() != null ? topPlace.category() : "landmark";
            double dist = topPlace.distanceMeters();
            String distText = dist < 1000
                    ? String.format(Locale.US, "about %.0f meters away", dist)
                    : String.format(Locale.US, "about %.1f kilometers ahead", dist / 1000.0);

            if (cat.contains("fort") || cat.contains("palace") || placeName.toLowerCase().contains("fort")) {
                return String.format(
                        "Just %s, you're passing near the historic %s in %s. " +
                        "This formidable fortress once stood as a guardian of regional trade routes, witnessing centuries of royal pageantry and epic battles. " +
                        "Look out towards the skyline to admire its enduring stonework and storied ramparts.",
                        distText, placeName, cleanCity
                );
            }

            if (cat.contains("temple") || cat.contains("cultural") || placeName.toLowerCase().contains("temple")) {
                return String.format(
                        "Up ahead %s lies %s, one of the notable spiritual landmarks of %s. " +
                        "Revered for its intricate traditional craftsmanship and serene ambiance, it draws travelers and pilgrims from across the region. " +
                        "Take in the peaceful atmosphere as you continue your journey.",
                        distText, placeName, cleanCity
                );
            }

            if (cat.contains("monument") || cat.contains("memorial")) {
                return String.format(
                        "You are currently approaching %s, located %s in %s. " +
                        "Erected to commemorate pivotal moments in local history, this monument stands as an iconic symbol of the area's proud civic heritage. " +
                        "It offers a timeless glimpse into the culture of the region.",
                        placeName, distText, cleanCity
                );
            }

            if (cat.contains("natural") || placeName.toLowerCase().contains("lake") || placeName.toLowerCase().contains("river")) {
                return String.format(
                        "Enjoy the scenic view as you travel near %s, situated %s. " +
                        "These natural waters and surrounding landscapes provide a vital sanctuary for local birdlife and a refreshing contrast to the road ahead. " +
                        "Breathe in the open expanse as your drive unfolds.",
                        placeName, distText
                );
            }

            return String.format(
                    "As you travel through %s, you are passing within %s of %s. " +
                    "Known locally as an important %s, this destination carries distinctive tales of local life and regional craftsmanship. " +
                    "Keep an eye out as your route takes you past this fascinating spot.",
                    cleanCity, distText, placeName, cat.replace("_", " ")
            );
        }

        // 3. City-level fallback narrations
        if (cleanCity.equalsIgnoreCase("Hyderabad")) {
            return "You are currently travelling through Hyderabad, Telangana. " +
                    "Known as the City of Pearls, Hyderabad effortlessly blends four centuries of royal heritage with modern technological innovation. " +
                    "As you pass through, look around for the historic architectural silhouettes and the irresistible aroma of authentic Hyderabadi biryani. " +
                    "Enjoy the rhythm of your journey through this historic crossroads of Deccan culture.";
        }

        if (cleanCity.equalsIgnoreCase("Bengaluru") || cleanCity.equalsIgnoreCase("Bangalore")) {
            return "Welcome to Bengaluru, the Garden City and technology heart of India. " +
                    "Blessed with pleasant weather and verdant tree-lined boulevards, Bengaluru represents the dynamic spirit of modern exploration. " +
                    "From historic botanical gardens to vibrant street cafes, every road here carries an energetic story. Enjoy your drive through the city.";
        }

        if (cleanCity.equalsIgnoreCase("Mumbai")) {
            return "You are passing through the vibrant metropolis of Mumbai on the Arabian Sea coast. " +
                    "From colonial-era Victorian architecture to the bustling energy of the coastal roads, Mumbai is a city of dreams that never sleeps. " +
                    "Take a moment to absorb the unique pulse and sea breeze as your journey continues.";
        }

        if (cleanCity.equalsIgnoreCase("Delhi") || cleanCity.equalsIgnoreCase("New Delhi")) {
            return "You are travelling through Delhi, a capital city that has stood at the crossroads of empires for thousands of years. " +
                    "Along these streets, ancient Mughal monuments and grand colonial avenues tell tales of dynasties, poets, and travellers across centuries. " +
                    "Enjoy your journey through this living museum of history.";
        }

        // Generic fallback with optional weather note
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("You're currently travelling through %s", cleanCity));
        if (state != null && !state.isBlank()) {
            sb.append(", ").append(state);
        }
        sb.append(". This area is celebrated for its distinctive regional history, vibrant local culture, and enduring community traditions. ");

        if (weather != null && isDistinctiveWeather(weather.condition())) {
            sb.append(String.format("The current %s skies set an atmospheric tone for your journey. ",
                    weather.condition().toLowerCase()));
        }

        sb.append("Relax, take in the sights along your route, and enjoy the travel story ahead.");
        return sb.toString();
    }

    private boolean isDistinctiveWeather(String condition) {
        if (condition == null) return false;
        String c = condition.toLowerCase();
        return c.contains("rain") || c.contains("storm") || c.contains("snow") ||
                c.contains("fog") || c.contains("mist") || c.contains("drizzle");
    }

    private String sanitizeStoryText(String text) {
        return text.replaceAll("[#*_`]", "")
                .replaceAll("^\\s*[-•*]\\s+", "")
                .trim();
    }
}
