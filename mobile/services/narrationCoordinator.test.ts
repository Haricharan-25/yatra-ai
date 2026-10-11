import { describe, it } from 'node:test';
import assert from 'node:assert';
import {
  NarrationCoordinator,
  getStoryDeduplicationKey,
} from './narrationCoordinator.ts';
import type { JourneyLocationResponse, Place } from '../types';
import type { SpeakOptions } from './speechService';

class MockSpeechHarness {
  public spokenTexts: string[] = [];
  public stopCallCount: number = 0;
  public lastOptions: SpeakOptions | null = null;
  public activeText: string | null = null;

  public speak = (text: string, options?: SpeakOptions) => {
    this.spokenTexts.push(text);
    this.lastOptions = options || null;
    this.activeText = text;
    // Simulate speech starting
    options?.onStart?.();
  };

  public stop = async () => {
    this.stopCallCount++;
    this.activeText = null;
    this.lastOptions?.onStopped?.();
  };

  public finishCurrent = () => {
    this.activeText = null;
    this.lastOptions?.onDone?.();
  };

  public failCurrent = (err: Error) => {
    this.activeText = null;
    this.lastOptions?.onError?.(err);
  };
}

function createMockResponse(overrides: Partial<JourneyLocationResponse> = {}): JourneyLocationResponse {
  const topPlace: Place = overrides.topPlace !== undefined ? overrides.topPlace! : {
    id: 'place-101',
    name: 'Golconda Fort',
    category: 'fort_palace',
    latitude: 17.38,
    longitude: 78.40,
    distanceMeters: 450,
    importanceScore: 10,
  };

  return {
    journeyId: 'test-journey',
    city: 'Hyderabad',
    state: 'Telangana',
    country: 'India',
    displayName: 'Hyderabad, Telangana, India',
    nearbyPlaces: topPlace ? [topPlace] : [],
    topPlace: topPlace,
    weather: null,
    triggerStory: true,
    triggerReason: 'NEARBY_MAJOR_LANDMARK',
    story: 'Majestic Golconda Fort stands on an 11-kilometer granite hill.',
    durationSeconds: 20,
    ...overrides,
  };
}

describe('NarrationCoordinator Automatic Narration Engine', () => {
  it('1. A new landmark story automatically starts speaking', () => {
    const harness = new MockSpeechHarness();
    const coordinator = new NarrationCoordinator(harness.speak, harness.stop);

    coordinator.start();

    const response = createMockResponse();
    const triggered = coordinator.handleJourneyResponse(response);

    assert.strictEqual(triggered, true, 'Should trigger playback');
    assert.strictEqual(harness.spokenTexts.length, 1);
    assert.strictEqual(harness.spokenTexts[0], response.story);
    assert.strictEqual(coordinator.getStatus(), 'narrating');
    assert.strictEqual(coordinator.getIsSpeaking(), true);
  });

  it('2. Repeated GPS responses containing the same story do not cause repeated narration', () => {
    const harness = new MockSpeechHarness();
    const coordinator = new NarrationCoordinator(harness.speak, harness.stop);

    coordinator.start();

    const response = createMockResponse();
    coordinator.handleJourneyResponse(response);
    assert.strictEqual(harness.spokenTexts.length, 1);

    // Second ping with identical landmark and story
    const secondPing = createMockResponse();
    const triggeredAgain = coordinator.handleJourneyResponse(secondPing);

    assert.strictEqual(triggeredAgain, false, 'Should be detected as duplicate');
    assert.strictEqual(harness.spokenTexts.length, 1, 'Should NOT speak again');
  });

  it('3. A different eligible story is handled after the current narration finishes', () => {
    const harness = new MockSpeechHarness();
    const coordinator = new NarrationCoordinator(harness.speak, harness.stop);

    coordinator.start();

    // Story 1: Golconda Fort
    const resp1 = createMockResponse({
      topPlace: { id: 'place-101', name: 'Golconda Fort', category: 'fort', latitude: 17.38, longitude: 78.4, distanceMeters: 300, importanceScore: 10 },
      story: 'Story about Golconda Fort',
    });
    coordinator.handleJourneyResponse(resp1);
    assert.strictEqual(harness.spokenTexts.length, 1);
    assert.strictEqual(coordinator.getIsSpeaking(), true);

    // Story 2: Charminar arrives while Story 1 is actively speaking
    const resp2 = createMockResponse({
      topPlace: { id: 'place-202', name: 'Charminar', category: 'monument', latitude: 17.36, longitude: 78.47, distanceMeters: 400, importanceScore: 10 },
      story: 'Story about Charminar',
    });
    const triggeredDuringPlayback = coordinator.handleJourneyResponse(resp2);

    // Should NOT interrupt Story 1
    assert.strictEqual(triggeredDuringPlayback, false);
    assert.strictEqual(harness.spokenTexts.length, 1, 'Story 1 continues uninterrupted');
    assert.strictEqual(coordinator.getPendingStory()?.placeName, 'Charminar');

    // When Story 1 finishes, Story 2 should automatically start speaking!
    harness.finishCurrent();

    assert.strictEqual(harness.spokenTexts.length, 2, 'Story 2 started automatically after Story 1 finished');
    assert.strictEqual(harness.spokenTexts[1], 'Story about Charminar');
    assert.strictEqual(coordinator.getIsSpeaking(), true);
  });

  it('4. Auto Narration OFF prevents automatic speech', async () => {
    const harness = new MockSpeechHarness();
    const coordinator = new NarrationCoordinator(harness.speak, harness.stop);

    coordinator.start();
    await coordinator.setAutoNarration(false);

    const response = createMockResponse();
    const triggered = coordinator.handleJourneyResponse(response);

    assert.strictEqual(triggered, false, 'Should not trigger auto speech');
    assert.strictEqual(harness.spokenTexts.length, 0, 'No speech should be spoken');
    assert.strictEqual(coordinator.getStatus(), 'disabled');
    assert.strictEqual(coordinator.getCurrentStory()?.story, response.story, 'Story text should be saved for display');
  });

  it('5. Switching Auto Narration OFF during playback stops speech and clears pending automatic playback', async () => {
    const harness = new MockSpeechHarness();
    const coordinator = new NarrationCoordinator(harness.speak, harness.stop);

    coordinator.start();

    // Start Story 1
    const resp1 = createMockResponse({ story: 'First Story' });
    coordinator.handleJourneyResponse(resp1);
    assert.strictEqual(coordinator.getIsSpeaking(), true);

    // Queue Story 2
    const resp2 = createMockResponse({
      topPlace: { id: 'p2', name: 'Second Place', category: 'attraction', latitude: 17.3, longitude: 78.3, distanceMeters: 500, importanceScore: 8 },
      story: 'Second Story',
    });
    coordinator.handleJourneyResponse(resp2);
    assert.notStrictEqual(coordinator.getPendingStory(), null);

    // Toggle Auto Narration OFF
    await coordinator.setAutoNarration(false);

    assert.strictEqual(harness.stopCallCount, 1, 'Should call stop');
    assert.strictEqual(coordinator.getIsSpeaking(), false);
    assert.strictEqual(coordinator.getPendingStory(), null, 'Queue should be cleared');
    assert.strictEqual(coordinator.getStatus(), 'disabled');
  });

  it('6. Pause and resume do not unexpectedly replay the current story', async () => {
    const harness = new MockSpeechHarness();
    const coordinator = new NarrationCoordinator(harness.speak, harness.stop);

    coordinator.start();

    const response = createMockResponse();
    coordinator.handleJourneyResponse(response);
    assert.strictEqual(harness.spokenTexts.length, 1);

    // Pause journey
    await coordinator.pause();
    assert.strictEqual(coordinator.getIsSpeaking(), false);
    assert.strictEqual(harness.stopCallCount, 1);

    // Resume journey
    coordinator.resume();
    assert.strictEqual(harness.spokenTexts.length, 1, 'Should NOT replay the story from the beginning');

    // Receiving the same story again after resume should not replay
    coordinator.handleJourneyResponse(response);
    assert.strictEqual(harness.spokenTexts.length, 1, 'Should still not replay duplicate story');
  });

  it('7. Stop prevents queued stories and late network responses from starting speech', async () => {
    const harness = new MockSpeechHarness();
    const coordinator = new NarrationCoordinator(harness.speak, harness.stop);

    coordinator.start();

    const resp1 = createMockResponse({ story: 'Story 1' });
    coordinator.handleJourneyResponse(resp1);

    const resp2 = createMockResponse({
      topPlace: { id: 'p2', name: 'Place 2', category: 'attraction', latitude: 17.1, longitude: 78.1, distanceMeters: 400, importanceScore: 8 },
      story: 'Story 2',
    });
    coordinator.handleJourneyResponse(resp2);

    // Stop journey
    await coordinator.stop();
    assert.strictEqual(coordinator.getIsSpeaking(), false);
    assert.strictEqual(coordinator.getPendingStory(), null);

    // Late network response arriving after stop
    const lateResponse = createMockResponse({
      topPlace: { id: 'p3', name: 'Place 3', category: 'attraction', latitude: 17.2, longitude: 78.2, distanceMeters: 400, importanceScore: 8 },
      story: 'Story 3',
    });
    const triggeredLate = coordinator.handleJourneyResponse(lateResponse);

    assert.strictEqual(triggeredLate, false, 'Late response must not start speech');
    assert.strictEqual(harness.spokenTexts.length, 1, 'No new speech after stop');
  });

  it('8. Null, empty, and non-triggered story responses do not start speech', () => {
    const harness = new MockSpeechHarness();
    const coordinator = new NarrationCoordinator(harness.speak, harness.stop);

    coordinator.start();

    // triggerStory: false
    const respFalse = createMockResponse({ triggerStory: false, story: 'Some text' });
    assert.strictEqual(coordinator.handleJourneyResponse(respFalse), false);

    // story: null
    const respNull = createMockResponse({ triggerStory: true, story: null });
    assert.strictEqual(coordinator.handleJourneyResponse(respNull), false);

    // story: empty whitespace
    const respEmpty = createMockResponse({ triggerStory: true, story: '   ' });
    assert.strictEqual(coordinator.handleJourneyResponse(respEmpty), false);

    assert.strictEqual(harness.spokenTexts.length, 0);
  });

  it('9. Speech errors do not break location tracking or throw unhandled exceptions', () => {
    const harness = new MockSpeechHarness();
    const coordinator = new NarrationCoordinator(harness.speak, harness.stop);

    coordinator.start();

    const response = createMockResponse();
    coordinator.handleJourneyResponse(response);

    // Simulate speech playback failure
    harness.failCurrent(new Error('Audio device busy'));

    assert.strictEqual(coordinator.getIsSpeaking(), false);
    assert.strictEqual(coordinator.getStatus(), 'error');

    // Subsequent valid story still processes cleanly
    const nextResponse = createMockResponse({
      topPlace: { id: 'next-place', name: 'Qutb Shahi Tombs', category: 'heritage', latitude: 17.39, longitude: 78.39, distanceMeters: 600, importanceScore: 9 },
      story: 'Seven royal tombs of the Qutb Shahi dynasty.',
    });
    const nextTriggered = coordinator.handleJourneyResponse(nextResponse);
    assert.strictEqual(nextTriggered, true);
    assert.strictEqual(harness.spokenTexts.length, 2);
  });

  it('10. Deduplication key generation is stable and deterministic across place types and fallbacks', () => {
    const keyWithId = getStoryDeduplicationKey({
      triggerReason: 'NEARBY_MAJOR_LANDMARK',
      topPlace: { id: 'osm_1234', name: 'Hawa Mahal', category: 'palace', latitude: 26.9, longitude: 75.8, distanceMeters: 200, importanceScore: 10 },
      city: 'Jaipur',
    });
    assert.strictEqual(keyWithId, 'NEARBY_MAJOR_LANDMARK:place_id:osm_1234');

    const keyWithName = getStoryDeduplicationKey({
      triggerReason: 'NEARBY_ATTRACTION',
      topPlace: { id: '', name: 'Birla Temple', category: 'temple', latitude: 26.8, longitude: 75.8, distanceMeters: 300, importanceScore: 8 },
      city: 'Jaipur',
    });
    assert.strictEqual(keyWithName, 'NEARBY_ATTRACTION:place_name:birla temple');

    const keyWithCity = getStoryDeduplicationKey({
      triggerReason: 'CITY_ENTERED',
      topPlace: null,
      city: 'Jaipur',
      state: 'Rajasthan',
    });
    assert.strictEqual(keyWithCity, 'CITY_ENTERED:city:jaipur_rajasthan');

    const keyWithFallbackText = getStoryDeduplicationKey({
      triggerReason: 'NONE',
      topPlace: null,
      city: '',
      story: 'Welcome to your next adventurous road trip!',
    });
    assert.strictEqual(keyWithFallbackText, 'NONE:text:welcome_to_your_next_adventurous');
  });

  it('11. Smart candidate replacement: major landmark replaces pending minor attraction in queue', () => {
    const harness = new MockSpeechHarness();
    const coordinator = new NarrationCoordinator(harness.speak, harness.stop);

    coordinator.start();

    // Start Story 1 (currently speaking)
    const activeStory = createMockResponse({ story: 'Initial active story' });
    coordinator.handleJourneyResponse(activeStory);
    assert.strictEqual(coordinator.getIsSpeaking(), true);

    // Minor attraction arrives (Priority 3, score 6)
    const minorAttraction = createMockResponse({
      topPlace: { id: 'view_1', name: 'Hill Viewpoint', category: 'viewpoint', latitude: 17.4, longitude: 78.4, distanceMeters: 400, importanceScore: 6 },
      triggerReason: 'NEARBY_ATTRACTION',
      story: 'Story about Hill Viewpoint',
    });
    coordinator.handleJourneyResponse(minorAttraction);
    assert.strictEqual(coordinator.getPendingStory()?.placeName, 'Hill Viewpoint');

    // Major landmark arrives (Priority 4, score 10)
    const majorLandmark = createMockResponse({
      topPlace: { id: 'fort_99', name: 'Chowmahalla Palace', category: 'fort_palace', latitude: 17.35, longitude: 78.46, distanceMeters: 500, importanceScore: 10 },
      triggerReason: 'NEARBY_MAJOR_LANDMARK',
      story: 'Story about Chowmahalla Palace',
    });
    coordinator.handleJourneyResponse(majorLandmark);

    // Major landmark should replace the minor viewpoint in pending queue!
    assert.strictEqual(coordinator.getPendingStory()?.placeName, 'Chowmahalla Palace');

    // When active story finishes, Chowmahalla Palace plays!
    harness.finishCurrent();
    assert.strictEqual(harness.spokenTexts[1], 'Story about Chowmahalla Palace');
  });

  it('12. Smart candidate replacement: closer place of same tier replaces further place', () => {
    const harness = new MockSpeechHarness();
    const coordinator = new NarrationCoordinator(harness.speak, harness.stop);

    coordinator.start();

    // Start active story
    coordinator.handleJourneyResponse(createMockResponse({ story: 'Active narration' }));

    // Place A (distance 1500m, score 10) arrives
    const placeFar = createMockResponse({
      topPlace: { id: 'fort_far', name: 'Far Fort', category: 'fort_palace', latitude: 17.5, longitude: 78.5, distanceMeters: 1500, importanceScore: 10 },
      triggerReason: 'NEARBY_MAJOR_LANDMARK',
      story: 'Far fort story',
    });
    coordinator.handleJourneyResponse(placeFar);
    assert.strictEqual(coordinator.getPendingStory()?.placeName, 'Far Fort');

    // Place B (distance 350m, score 10) arrives
    const placeClose = createMockResponse({
      topPlace: { id: 'fort_close', name: 'Close Fort', category: 'fort_palace', latitude: 17.4, longitude: 78.4, distanceMeters: 350, importanceScore: 10 },
      triggerReason: 'NEARBY_MAJOR_LANDMARK',
      story: 'Close fort story',
    });
    coordinator.handleJourneyResponse(placeClose);

    // Closer fort should replace the farther fort!
    assert.strictEqual(coordinator.getPendingStory()?.placeName, 'Close Fort');
  });

  it('13. Quiet tracking: non-triggered updates do not disrupt active narration or set error', () => {
    const harness = new MockSpeechHarness();
    const coordinator = new NarrationCoordinator(harness.speak, harness.stop);

    coordinator.start();

    // Start active story
    coordinator.handleJourneyResponse(createMockResponse({ story: 'Active narration' }));
    assert.strictEqual(coordinator.getStatus(), 'narrating');

    // Quiet update with triggerStory: false arrives while speaking
    const quietUpdate = createMockResponse({
      triggerStory: false,
      triggerReason: 'NONE',
      story: null,
    });
    const handled = coordinator.handleJourneyResponse(quietUpdate);

    assert.strictEqual(handled, false);
    assert.strictEqual(coordinator.getStatus(), 'narrating', 'Should remain in narrating state');
    assert.strictEqual(coordinator.getIsSpeaking(), true, 'Active audio must not be interrupted');
  });

  it('14. Route-aware advance narration: ROUTE_UPCOMING_LANDMARK automatically starts speech when idle', () => {
    const harness = new MockSpeechHarness();
    const coordinator = new NarrationCoordinator(harness.speak, harness.stop);

    coordinator.start();

    const routeLandmarkResponse = createMockResponse({
      triggerReason: 'ROUTE_UPCOMING_LANDMARK',
      story: 'Ahead on your route to Warangal, you will pass the historic Golconda Fort in 1.8 kilometers.',
      routeInfo: {
        destinationName: 'Warangal',
        totalDistanceMeters: 147000,
        remainingDistanceMeters: 145000,
        progressPercent: 1.5,
      },
    });

    const handled = coordinator.handleJourneyResponse(routeLandmarkResponse);
    assert.strictEqual(handled, true);
    assert.strictEqual(harness.spokenTexts.length, 1);
    assert.match(harness.spokenTexts[0], /Warangal/);
    assert.strictEqual(coordinator.getStatus(), 'narrating');
  });

  it('15. Candidate replacement: upcoming route landmark (priority 5) replaces pending attraction (priority 3)', () => {
    const harness = new MockSpeechHarness();
    const coordinator = new NarrationCoordinator(harness.speak, harness.stop);

    coordinator.start();

    // Start active story
    coordinator.handleJourneyResponse(createMockResponse({ story: 'Currently speaking intro' }));

    // Enqueue normal nearby attraction
    const normalAttraction = createMockResponse({
      topPlace: { id: 'market-1', name: 'Local Market', category: 'viewpoint', latitude: 17.38, longitude: 78.48, distanceMeters: 500, importanceScore: 6 },
      triggerReason: 'NEARBY_ATTRACTION',
      story: 'Story about local market',
    });
    coordinator.handleJourneyResponse(normalAttraction);
    assert.strictEqual(coordinator.getPendingStory()?.placeName, 'Local Market');

    // Upcoming route major landmark arrives
    const routeMajor = createMockResponse({
      topPlace: { id: 'fort-ahead', name: 'Warangal Gateway', category: 'fort_palace', latitude: 17.9, longitude: 79.5, distanceMeters: 1800, importanceScore: 10 },
      triggerReason: 'ROUTE_UPCOMING_LANDMARK',
      story: 'Upcoming route gateway story',
    });
    coordinator.handleJourneyResponse(routeMajor);

    // Route major landmark must supersede normal nearby attraction in queue
    assert.strictEqual(coordinator.getPendingStory()?.placeName, 'Warangal Gateway');
  });

  it('16. Route deduplication: previously played route landmark is never repeated on subsequent updates', () => {
    const harness = new MockSpeechHarness();
    const coordinator = new NarrationCoordinator(harness.speak, harness.stop);

    coordinator.start();

    const routeResp = createMockResponse({
      topPlace: { id: 'landmark-77', name: 'Thousand Pillar Temple', category: 'cultural_temple', latitude: 17.98, longitude: 79.58, distanceMeters: 1200, importanceScore: 9 },
      triggerReason: 'ROUTE_UPCOMING_LANDMARK',
      story: 'Ahead on your route to Warangal lies the Thousand Pillar Temple.',
    });

    // Play first time
    coordinator.handleJourneyResponse(routeResp);
    assert.strictEqual(harness.spokenTexts.length, 1);

    // Finish current speech
    harness.finishCurrent();
    assert.strictEqual(coordinator.getStatus(), 'finished');

    // Subsequent GPS update contains the same route landmark
    const repeated = coordinator.handleJourneyResponse(routeResp);
    assert.strictEqual(repeated, false, 'Duplicate route landmark must be suppressed');
    assert.strictEqual(harness.spokenTexts.length, 1, 'Speech count must remain 1');
  });
});

