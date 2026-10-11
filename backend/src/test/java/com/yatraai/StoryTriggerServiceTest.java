package com.yatraai;

import com.yatraai.model.JourneyEvent;
import com.yatraai.model.JourneyState;
import com.yatraai.model.Place;
import com.yatraai.service.StoryTriggerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class StoryTriggerServiceTest {

    @Autowired
    private StoryTriggerService storyTriggerService;

    @BeforeEach
    void setUp() {
        storyTriggerService.resetJourney();
        storyTriggerService.setCooldownSeconds(300); // 5 min cooldown
    }

    @Test
    @DisplayName("Initial location detection triggers CITY_ENTERED")
    void testInitialCityEntered() {
        StoryTriggerService.TriggerDecision decision = storyTriggerService.evaluateTrigger(
                "Hyderabad", "Telangana", List.of(), Instant.now()
        );

        assertTrue(decision.triggerStory());
        assertEquals(JourneyEvent.CITY_ENTERED, decision.event());
    }

    @Test
    @DisplayName("City change triggers CITY_CHANGED event")
    void testCityChangeDetection() {
        // First city
        storyTriggerService.recordNarration("Hyderabad", "Telangana", null, "Story about Hyderabad");

        // Travel 10 minutes later into a new city
        Instant futureTime = Instant.now().plusSeconds(600);
        StoryTriggerService.TriggerDecision decision = storyTriggerService.evaluateTrigger(
                "Secunderabad", "Telangana", List.of(), futureTime
        );

        assertTrue(decision.triggerStory());
        assertEquals(JourneyEvent.CITY_CHANGED, decision.event());
    }

    @Test
    @DisplayName("State change triggers REGION_CHANGED event")
    void testRegionChangeDetection() {
        // First city and state
        storyTriggerService.recordNarration("Hyderabad", "Telangana", null, "Story about Telangana");

        // Travel into Karnataka
        Instant futureTime = Instant.now().plusSeconds(600);
        StoryTriggerService.TriggerDecision decision = storyTriggerService.evaluateTrigger(
                "Bidar", "Karnataka", List.of(), futureTime
        );

        assertTrue(decision.triggerStory());
        assertEquals(JourneyEvent.REGION_CHANGED, decision.event());
    }

    @Test
    @DisplayName("Same city within cooldown does not trigger a duplicate story")
    void testSameCityWithinCooldown() {
        storyTriggerService.recordNarration("Hyderabad", "Telangana", null, "Story about Hyderabad");

        // 30 seconds later in the same city with no POIs
        Instant soonAfter = Instant.now().plusSeconds(30);
        StoryTriggerService.TriggerDecision decision = storyTriggerService.evaluateTrigger(
                "Hyderabad", "Telangana", List.of(), soonAfter
        );

        assertFalse(decision.triggerStory());
        assertEquals(JourneyEvent.NONE, decision.event());
    }

    @Test
    @DisplayName("Nearby major landmark within 2km triggers NEARBY_MAJOR_LANDMARK")
    void testNearbyMajorLandmarkTrigger() {
        Place fort = new Place("fort_1", "Golconda Fort", "fort_palace", "Historic fort", 17.38, 78.40, 850.0, 10);

        // Previous story narrated 10 minutes ago
        storyTriggerService.recordNarration("Hyderabad", "Telangana", null, "Initial story");
        Instant futureTime = Instant.now().plusSeconds(600);

        StoryTriggerService.TriggerDecision decision = storyTriggerService.evaluateTrigger(
                "Hyderabad", "Telangana", List.of(fort), futureTime
        );

        assertTrue(decision.triggerStory());
        assertEquals(JourneyEvent.NEARBY_MAJOR_LANDMARK, decision.event());
        assertNotNull(decision.targetPlace());
        assertEquals("Golconda Fort", decision.targetPlace().name());
    }

    @Test
    @DisplayName("Nearby natural attraction within 1.5km triggers NEARBY_ATTRACTION")
    void testNearbyNaturalAttractionTrigger() {
        Place lake = new Place("lake_1", "Hussain Sagar Lake", "natural_attraction", "Scenic lake", 17.42, 78.47, 1200.0, 8);

        storyTriggerService.recordNarration("Hyderabad", "Telangana", null, "Initial story");
        Instant futureTime = Instant.now().plusSeconds(600);

        StoryTriggerService.TriggerDecision decision = storyTriggerService.evaluateTrigger(
                "Hyderabad", "Telangana", List.of(lake), futureTime
        );

        assertTrue(decision.triggerStory());
        assertEquals(JourneyEvent.NEARBY_ATTRACTION, decision.event());
        assertNotNull(decision.targetPlace());
        assertEquals("Hussain Sagar Lake", decision.targetPlace().name());
    }

    @Test
    @DisplayName("Duplicate POI prevention ensures a narrated landmark is not triggered again")
    void testDuplicatePoiPrevention() {
        Place fort = new Place("fort_1", "Golconda Fort", "fort_palace", "Historic fort", 17.38, 78.40, 850.0, 10);

        // Narrate Golconda Fort
        storyTriggerService.recordNarration("Hyderabad", "Telangana", fort, "Story about Golconda Fort");

        // 10 minutes later, still near Golconda Fort
        Instant futureTime = Instant.now().plusSeconds(600);
        StoryTriggerService.TriggerDecision decision = storyTriggerService.evaluateTrigger(
                "Hyderabad", "Telangana", List.of(fort), futureTime
        );

        // Should NOT trigger again for Golconda Fort
        assertFalse(decision.triggerStory());
        assertEquals(JourneyEvent.NONE, decision.event());
    }

    @Test
    @DisplayName("Paused and stopped journeys do not trigger stories")
    void testPausedAndStoppedJourneyDecisions() {
        JourneyState journey = storyTriggerService.getOrCreateJourney("paused_test_journey");
        journey.pause();

        Place fort = new Place("fort_1", "Golconda Fort", "fort_palace", "Historic fort", 17.38, 78.40, 500.0, 10);

        StoryTriggerService.TriggerDecision pausedDecision = storyTriggerService.evaluateTrigger(
                journey, "Hyderabad", "Telangana", List.of(fort), Instant.now()
        );
        assertFalse(pausedDecision.triggerStory());
        assertEquals("Journey is paused", pausedDecision.reason());

        journey.stop();
        StoryTriggerService.TriggerDecision stoppedDecision = storyTriggerService.evaluateTrigger(
                journey, "Hyderabad", "Telangana", List.of(fort), Instant.now()
        );
        assertFalse(stoppedDecision.triggerStory());
        assertEquals("Journey is stopped", stoppedDecision.reason());
    }

    @Test
    @DisplayName("Resetting journey clears all previous narration records and cities")
    void testResetJourney() {
        Place fort = new Place("fort_1", "Golconda Fort", "fort_palace", "Historic fort", 17.38, 78.40, 850.0, 10);
        storyTriggerService.recordNarration("Hyderabad", "Telangana", fort, "Story");

        storyTriggerService.resetJourney();

        assertNull(storyTriggerService.getPreviousCity());
        assertTrue(storyTriggerService.getNarratedPlaceNames().isEmpty());
        assertTrue(storyTriggerService.getPreviousStories().isEmpty());
    }

    @Test
    @DisplayName("Smart Selection: Prioritizes major historical landmark over closer minor attraction")
    void testPrioritizeMajorLandmarkOverMinorAttraction() {
        // Place 1: Major fort (Score 10, 1400m away, within 2000m trigger distance)
        Place fort = new Place("fort_10", "Golconda Fort", "fort_palace", "Historic fort", 17.38, 78.40, 1400.0, 10);

        // Place 2: Minor viewpoint (Score 6, 250m away, within 1000m trigger distance)
        Place viewpoint = new Place("view_1", "City Viewpoint", "viewpoint", "Scenic viewpoint", 17.39, 78.41, 250.0, 6);

        storyTriggerService.recordNarration("Hyderabad", "Telangana", null, "Initial city story");
        Instant futureTime = Instant.now().plusSeconds(600);

        // Even though viewpoint is in list and closer, fort is higher importance tier!
        StoryTriggerService.TriggerDecision decision = storyTriggerService.evaluateTrigger(
                "Hyderabad", "Telangana", List.of(viewpoint, fort), futureTime
        );

        assertTrue(decision.triggerStory());
        assertEquals(JourneyEvent.NEARBY_MAJOR_LANDMARK, decision.event());
        assertNotNull(decision.targetPlace());
        assertEquals("Golconda Fort", decision.targetPlace().name());
    }

    @Test
    @DisplayName("Smart Selection: Prioritizes closer place when importance scores are identical")
    void testPrioritizeCloserPlaceWithinSameTier() {
        Place farFort = new Place("fort_far", "Rachakonda Fort", "fort_palace", "Hill fort", 17.2, 78.8, 1600.0, 10);
        Place closeFort = new Place("fort_close", "Golconda Fort", "fort_palace", "Royal fortress", 17.38, 78.40, 450.0, 10);

        storyTriggerService.recordNarration("Hyderabad", "Telangana", null, "Initial city story");
        Instant futureTime = Instant.now().plusSeconds(600);

        StoryTriggerService.TriggerDecision decision = storyTriggerService.evaluateTrigger(
                "Hyderabad", "Telangana", List.of(farFort, closeFort), futureTime
        );

        assertTrue(decision.triggerStory());
        assertEquals("Golconda Fort", decision.targetPlace().name());
    }

    @Test
    @DisplayName("Smart Selection: Skips already narrated place and picks next best eligible place")
    void testSkippingNarratedPlacePicksNextBest() {
        Place fort = new Place("fort_1", "Golconda Fort", "fort_palace", "Historic fort", 17.38, 78.40, 500.0, 10);
        Place charminar = new Place("monument_1", "Charminar", "monument", "Grand monument", 17.36, 78.47, 800.0, 9);

        // Fort already narrated
        storyTriggerService.recordNarration("Hyderabad", "Telangana", fort, "Story about fort");
        Instant futureTime = Instant.now().plusSeconds(600);

        StoryTriggerService.TriggerDecision decision = storyTriggerService.evaluateTrigger(
                "Hyderabad", "Telangana", List.of(fort, charminar), futureTime
        );

        assertTrue(decision.triggerStory());
        assertEquals("Charminar", decision.targetPlace().name());
    }

    @Test
    @DisplayName("Smart Selection: Out of range places track quietly without triggering filler")
    void testOutOfRangePlacesTrackQuietly() {
        // Place > 2000m for major landmark, or > 1000m for normal attraction
        Place farFort = new Place("fort_far", "Distant Fort", "fort_palace", "Distant fort", 17.1, 78.1, 2800.0, 10);
        Place farAttraction = new Place("attr_far", "Distant Garden", "attraction", "Garden", 17.2, 78.2, 1800.0, 6);

        storyTriggerService.recordNarration("Hyderabad", "Telangana", null, "Initial city story");
        Instant futureTime = Instant.now().plusSeconds(600);

        StoryTriggerService.TriggerDecision decision = storyTriggerService.evaluateTrigger(
                "Hyderabad", "Telangana", List.of(farFort, farAttraction), futureTime
        );

        assertFalse(decision.triggerStory(), "Should track quietly without triggering filler story");
        assertEquals(JourneyEvent.NONE, decision.event());
        assertNull(decision.targetPlace());
    }

    @Test
    @DisplayName("Smart Selection: Cooldown active suppresses trigger even when eligible POI is near")
    void testCooldownSuppressesTriggerEvenNearPoi() {
        Place fort = new Place("fort_1", "Golconda Fort", "fort_palace", "Historic fort", 17.38, 78.40, 500.0, 10);

        storyTriggerService.recordNarration("Hyderabad", "Telangana", null, "Initial story");
        // Only 30 seconds later (cooldown is 300s)
        Instant soonAfter = Instant.now().plusSeconds(30);

        StoryTriggerService.TriggerDecision decision = storyTriggerService.evaluateTrigger(
                "Hyderabad", "Telangana", List.of(fort), soonAfter
        );

        assertFalse(decision.triggerStory(), "Cooldown must prevent story trigger");
        assertEquals(JourneyEvent.NONE, decision.event());
    }
}
