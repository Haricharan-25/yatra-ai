import type {
  JourneyEvent,
  JourneyLocationResponse,
  Place,
  StoryResponse,
} from '../types';
import type { SpeakOptions } from './speechService';

export type NarrationStatus =
  | 'idle'
  | 'waiting'
  | 'preparing'
  | 'narrating'
  | 'finished'
  | 'disabled'
  | 'error';

export interface QueuedStory {
  id: string;
  story: string;
  durationSeconds: number;
  placeName?: string;
  category?: string;
  triggerReason: JourneyEvent;
  city: string;
  state?: string;
  receivedAt: number;
  topPlace?: Place | null;
}

export interface NarrationListener {
  onStatusChange?: (status: NarrationStatus, message?: string | null) => void;
  onStoryChange?: (story: StoryResponse) => void;
  onSpeakingChange?: (isSpeaking: boolean) => void;
}

/**
 * Generates a stable, deterministic deduplication key for a story trigger response.
 * Priority:
 * 1. Target place ID: `${triggerReason}:place_id:${place.id}`
 * 2. Target place Name: `${triggerReason}:place_name:${place.name}`
 * 3. City/State identity: `${triggerReason}:city:${city}_${state}`
 * 4. Story content fallback: `${triggerReason}:text:${snippet}`
 */
export function getStoryDeduplicationKey(response: {
  triggerReason?: JourneyEvent;
  topPlace?: Place | null;
  city?: string;
  state?: string;
  story?: string | null;
}): string {
  const reason = response.triggerReason || 'NONE';
  const place = response.topPlace;

  if (place?.id && place.id.trim().length > 0) {
    return `${reason}:place_id:${place.id.trim()}`;
  }

  if (place?.name && place.name.trim().length > 0) {
    return `${reason}:place_name:${place.name.trim().toLowerCase()}`;
  }

  const city = response.city?.trim().toLowerCase();
  const state = response.state?.trim().toLowerCase();
  if (city && city !== 'unknown location') {
    return `${reason}:city:${city}${state ? `_${state}` : ''}`;
  }

  const textSample = (response.story || '')
    .trim()
    .slice(0, 32)
    .toLowerCase()
    .replace(/\s+/g, '_');
  return `${reason}:text:${textSample || 'unknown'}`;
}

function getEventPriority(reason: JourneyEvent): number {
  switch (reason) {
    case 'ROUTE_UPCOMING_LANDMARK':
      return 5;
    case 'NEARBY_MAJOR_LANDMARK':
      return 4;
    case 'ROUTE_UPCOMING_ATTRACTION':
      return 3.5;
    case 'NEARBY_ATTRACTION':
      return 3;
    case 'REGION_CHANGED':
    case 'CITY_CHANGED':
      return 2;
    case 'CITY_ENTERED':
      return 1;
    default:
      return 0;
  }
}

export class NarrationCoordinator {
  private isJourneyRunning: boolean = false;
  private autoNarrationEnabled: boolean = true;
  private status: NarrationStatus = 'idle';
  private statusMessage: string | null = null;
  private isSpeaking: boolean = false;

  private currentStory: QueuedStory | null = null;
  private pendingStory: QueuedStory | null = null;

  // Deduplication cache: tracks keys that have already been played or started
  private playedStoryKeys: Set<string> = new Set();
  // Failure tracker to avoid infinite retries on unplayable stories
  private failedAttempts: Map<string, number> = new Map();

  // Async token / session tracking to invalidate late responses after stop/reset
  private sessionId: number = 0;
  private utteranceCounter: number = 0;

  private listeners: Set<NarrationListener> = new Set();

  // Injected dependencies for unit testing
  private speakFn: (text: string, options?: SpeakOptions) => void;
  private stopFn: () => Promise<void>;

  constructor(
    customSpeakFn?: (text: string, options?: SpeakOptions) => void,
    customStopFn?: () => Promise<void>
  ) {
    this.speakFn = customSpeakFn || ((text, opts) => {
      try {
        const { speakStory } = require('./speechService');
        speakStory(text, opts);
      } catch (err) {
        opts?.onError?.(err instanceof Error ? err : new Error(String(err)));
      }
    });

    this.stopFn = customStopFn || (async () => {
      try {
        const { stopStory } = require('./speechService');
        await stopStory();
      } catch {}
    });
  }

  public subscribe(listener: NarrationListener): () => void {
    this.listeners.add(listener);
    // Push current states immediately upon subscription
    listener.onStatusChange?.(this.status, this.statusMessage);
    listener.onSpeakingChange?.(this.isSpeaking);
    if (this.currentStory) {
      listener.onStoryChange?.(this.toStoryResponse(this.currentStory));
    }

    return () => {
      this.listeners.delete(listener);
    };
  }

  public getStatus(): NarrationStatus {
    return this.status;
  }

  public getIsSpeaking(): boolean {
    return this.isSpeaking;
  }

  public getCurrentStory(): QueuedStory | null {
    return this.currentStory;
  }

  public getPendingStory(): QueuedStory | null {
    return this.pendingStory;
  }

  public getPlayedStoryKeys(): Set<string> {
    return new Set(this.playedStoryKeys);
  }

  public isAutoNarrationEnabled(): boolean {
    return this.autoNarrationEnabled;
  }

  /**
   * Initializes or restarts journey narration session.
   */
  public start(): void {
    this.sessionId++;
    this.isJourneyRunning = true;
    this.pendingStory = null;
    this.playedStoryKeys.clear();
    this.failedAttempts.clear();
    this.setStatus(this.autoNarrationEnabled ? 'waiting' : 'disabled');
  }

  /**
   * Pauses journey narration: stops active audio and clears pending candidate.
   * Stories that were already narrated will not replay on resume.
   */
  public async pause(): Promise<void> {
    this.isJourneyRunning = false;
    this.pendingStory = null;

    if (this.isSpeaking) {
      this.isSpeaking = false;
      this.notifySpeakingChange(false);
      await this.stopFn();
    }

    this.setStatus('idle');
  }

  /**
   * Resumes journey narration without replaying the previously finished story.
   */
  public resume(): void {
    this.isJourneyRunning = true;
    this.pendingStory = null;
    this.setStatus(this.autoNarrationEnabled ? 'waiting' : 'disabled');
  }

  /**
   * Stops journey narration, cancels pending candidates, stops speech,
   * and increments session ID to invalidate pending network callbacks.
   */
  public async stop(): Promise<void> {
    this.sessionId++;
    this.isJourneyRunning = false;
    this.pendingStory = null;
    this.playedStoryKeys.clear();
    this.failedAttempts.clear();

    if (this.isSpeaking) {
      this.isSpeaking = false;
      this.notifySpeakingChange(false);
      await this.stopFn();
    }

    this.currentStory = null;
    this.setStatus('idle');
  }

  /**
   * Toggles Auto Narration ON or OFF.
   * Turning OFF stops active speech and clears any queued story.
   */
  public async setAutoNarration(enabled: boolean): Promise<void> {
    this.autoNarrationEnabled = enabled;

    if (!enabled) {
      this.pendingStory = null;
      if (this.isSpeaking) {
        this.isSpeaking = false;
        this.notifySpeakingChange(false);
        await this.stopFn();
      }
      this.setStatus('disabled', 'Auto narration disabled');
    } else {
      if (this.isJourneyRunning && !this.isSpeaking) {
        this.setStatus('waiting');
      }
    }
  }

  /**
   * Handles incoming journey location response.
   * Returns true if automatic playback was initiated.
   */
  public handleJourneyResponse(response: JourneyLocationResponse): boolean {
    if (!this.isJourneyRunning) {
      return false;
    }

    // Must be an explicit trigger with non-empty story text
    if (
      !response.triggerStory ||
      !response.story ||
      typeof response.story !== 'string' ||
      response.story.trim().length === 0
    ) {
      return false;
    }

    const dedupKey = getStoryDeduplicationKey(response);

    // Skip if already played or currently active
    if (this.playedStoryKeys.has(dedupKey) || this.currentStory?.id === dedupKey) {
      return false;
    }

    const candidate: QueuedStory = {
      id: dedupKey,
      story: response.story.trim(),
      durationSeconds:
        response.durationSeconds ||
        Math.max(15, Math.round(response.story.split(/\s+/).length / 2.5)),
      placeName: response.topPlace?.name,
      category: response.topPlace?.category,
      triggerReason: response.triggerReason,
      city: response.city,
      state: response.state,
      receivedAt: Date.now(),
      topPlace: response.topPlace || null,
    };

    // If Auto Narration is turned OFF: update visual card, do not play
    if (!this.autoNarrationEnabled) {
      this.currentStory = candidate;
      this.notifyStoryChange(candidate);
      this.setStatus('disabled', 'Auto narration disabled • Tap Listen to play');
      return false;
    }

    // If narration is already speaking: queue this candidate (bounded queue of 1)
    if (this.isSpeaking) {
      this.enqueueCandidate(candidate);
      return false;
    }

    // Otherwise, immediately start audio narration
    this.playStory(candidate);
    return true;
  }

  /**
   * Enqueues a candidate story while another is playing.
   * Discards stale entries in favor of higher priority, closer, or newer candidate.
   */
  private enqueueCandidate(candidate: QueuedStory): void {
    if (!this.pendingStory) {
      this.pendingStory = candidate;
      return;
    }

    if (this.pendingStory.id === candidate.id) {
      return;
    }

    if (this.isCandidateSuperior(candidate, this.pendingStory)) {
      this.pendingStory = candidate;
    }
  }

  private isCandidateSuperior(newCandidate: QueuedStory, currentCandidate: QueuedStory): boolean {
    const newPrio = getEventPriority(newCandidate.triggerReason);
    const curPrio = getEventPriority(currentCandidate.triggerReason);

    if (newPrio > curPrio) {
      return true;
    }
    if (newPrio < curPrio) {
      return false;
    }

    // Same event priority tier: compare place importance scores
    const newScore = newCandidate.topPlace?.importanceScore ?? 0;
    const curScore = currentCandidate.topPlace?.importanceScore ?? 0;
    if (newScore > curScore) {
      return true;
    }
    if (newScore < curScore) {
      return false;
    }

    // Closer distance preferred within the same importance tier
    const newDist = newCandidate.topPlace?.distanceMeters ?? Number.MAX_VALUE;
    const curDist = currentCandidate.topPlace?.distanceMeters ?? Number.MAX_VALUE;
    if (newDist < curDist) {
      return true;
    }
    if (newDist > curDist) {
      return false;
    }

    // Deterministic tie breaker: lexicographical comparison of ID
    return newCandidate.id < currentCandidate.id;
  }

  /**
   * Plays a story candidate aloud via speech synthesis.
   */
  private playStory(candidate: QueuedStory): void {
    if (!this.isJourneyRunning || !this.autoNarrationEnabled) {
      return;
    }

    const session = this.sessionId;
    const utterance = ++this.utteranceCounter;

    this.currentStory = candidate;
    this.notifyStoryChange(candidate);
    this.setStatus('preparing');

    try {
      this.speakFn(candidate.story, {
        onStart: () => {
          if (this.sessionId !== session || this.utteranceCounter !== utterance) return;
          this.isSpeaking = true;
          this.playedStoryKeys.add(candidate.id);
          this.setStatus('narrating');
          this.notifySpeakingChange(true);
        },
        onDone: () => {
          if (this.sessionId !== session || this.utteranceCounter !== utterance) return;
          this.isSpeaking = false;
          this.setStatus('finished');
          this.notifySpeakingChange(false);
          this.playNextIfAvailable();
        },
        onStopped: () => {
          if (this.sessionId !== session || this.utteranceCounter !== utterance) return;
          this.isSpeaking = false;
          this.notifySpeakingChange(false);
        },
        onError: (err: Error) => {
          if (this.sessionId !== session || this.utteranceCounter !== utterance) return;
          this.isSpeaking = false;
          this.notifySpeakingChange(false);
          const attempts = (this.failedAttempts.get(candidate.id) || 0) + 1;
          this.failedAttempts.set(candidate.id, attempts);
          this.setStatus('error', err?.message || 'Narration playback failed');

          // If there is an alternative pending candidate, attempt it
          if (this.pendingStory && this.pendingStory.id !== candidate.id) {
            this.playNextIfAvailable();
          }
        },
      });
    } catch (err: any) {
      this.isSpeaking = false;
      this.notifySpeakingChange(false);
      this.setStatus('error', err?.message || 'Failed to initialize speech');
    }
  }

  /**
   * Plays the next pending candidate if one is waiting in the queue.
   */
  private playNextIfAvailable(): void {
    if (!this.isJourneyRunning || !this.autoNarrationEnabled) {
      this.pendingStory = null;
      return;
    }

    if (this.pendingStory) {
      const next = this.pendingStory;
      this.pendingStory = null;

      if (!this.playedStoryKeys.has(next.id)) {
        this.playStory(next);
      }
    }
  }

  /**
   * Manual user playback (via "Listen" button in StoryCard).
   */
  public speakManually(storyText: string, storyDetails?: Partial<StoryResponse>): void {
    if (!storyText || storyText.trim().length === 0) return;

    const session = this.sessionId;
    const utterance = ++this.utteranceCounter;

    this.pendingStory = null; // Manual listen clears automatic backlog
    this.setStatus('narrating');

    try {
      this.speakFn(storyText.trim(), {
        onStart: () => {
          if (this.sessionId !== session || this.utteranceCounter !== utterance) return;
          this.isSpeaking = true;
          this.notifySpeakingChange(true);
        },
        onDone: () => {
          if (this.sessionId !== session || this.utteranceCounter !== utterance) return;
          this.isSpeaking = false;
          this.setStatus('finished');
          this.notifySpeakingChange(false);
        },
        onStopped: () => {
          if (this.sessionId !== session || this.utteranceCounter !== utterance) return;
          this.isSpeaking = false;
          this.notifySpeakingChange(false);
        },
        onError: (err: Error) => {
          if (this.sessionId !== session || this.utteranceCounter !== utterance) return;
          this.isSpeaking = false;
          this.notifySpeakingChange(false);
          this.setStatus('error', err?.message || 'Manual playback failed');
        },
      });
    } catch (err: any) {
      this.isSpeaking = false;
      this.notifySpeakingChange(false);
      this.setStatus('error', err?.message || 'Failed to start speech');
    }
  }

  /**
   * Stops active speech playback manually.
   */
  public async stopSpeaking(): Promise<void> {
    this.pendingStory = null;
    if (this.isSpeaking) {
      this.isSpeaking = false;
      this.notifySpeakingChange(false);
    }
    await this.stopFn();
    this.setStatus(this.isJourneyRunning ? 'waiting' : 'idle');
  }

  private setStatus(status: NarrationStatus, message: string | null = null): void {
    this.status = status;
    this.statusMessage = message;
    this.listeners.forEach((l) => l.onStatusChange?.(status, message));
  }

  private notifyStoryChange(story: QueuedStory): void {
    const resp = this.toStoryResponse(story);
    this.listeners.forEach((l) => l.onStoryChange?.(resp));
  }

  private notifySpeakingChange(isSpeaking: boolean): void {
    this.listeners.forEach((l) => l.onSpeakingChange?.(isSpeaking));
  }

  private toStoryResponse(story: QueuedStory): StoryResponse {
    return {
      city: story.city,
      story: story.story,
      durationSeconds: story.durationSeconds,
      placeName: story.placeName,
      category: story.category,
      triggerReason: story.triggerReason,
    };
  }
}

export const narrationCoordinator = new NarrationCoordinator();
