import * as Location from 'expo-location';
import { LocationCoordinates, JourneyLocationResponse, JourneyStatus } from '../types';
import { requestLocationPermission } from './locationService';
import {
  sendJourneyLocation,
  resetJourneyBackend,
  startJourneyBackend,
  pauseJourneyBackend,
  resumeJourneyBackend,
  stopJourneyBackend,
} from './apiService';

export interface JourneyCallbacks {
  onLocationUpdate?: (coords: LocationCoordinates) => void;
  onJourneyResponse?: (response: JourneyLocationResponse) => void;
  onError?: (error: Error) => void;
  onStatusChange?: (status: JourneyStatus) => void;
}

class JourneyTrackingManager {
  private subscription: Location.LocationSubscription | null = null;
  private status: JourneyStatus = 'idle';
  private journeyId: string = 'journey_default';
  private autoNarrationEnabled: boolean = true;
  private callbacks: JourneyCallbacks = {};
  private lastSentCoords: LocationCoordinates | null = null;
  private lastSentTime: number = 0;
  private isPingInProgress: boolean = false;
  private destination: string | null = null;

  public getJourneyStatus(): JourneyStatus {
    return this.status;
  }

  public isJourneyActive(): boolean {
    return this.status === 'running';
  }

  public isJourneyPaused(): boolean {
    return this.status === 'paused';
  }

  public getJourneyId(): string {
    return this.journeyId;
  }

  public setAutoNarration(enabled: boolean) {
    this.autoNarrationEnabled = enabled;
  }

  public isAutoNarrationEnabled(): boolean {
    return this.autoNarrationEnabled;
  }

  public setDestination(destination: string | null) {
    this.destination = destination;
  }

  public getDestination(): string | null {
    return this.destination;
  }

  /**
   * Starts a continuous travel journey:
   * 1. Initializes unique journeyId
   * 2. Resets backend in-memory journey state
   * 3. Requests foreground location permissions gracefully
   * 4. Configures periodic GPS tracking with 20s interval / 50m filter
   */
  public async startJourney(callbacks: JourneyCallbacks, existingJourneyId?: string): Promise<void> {
    if (this.status === 'running') {
      return;
    }

    this.callbacks = callbacks;
    this.journeyId = existingJourneyId || `journey_${Date.now()}`;

    // Graceful permission check
    const perm = await requestLocationPermission();
    if (!perm.granted) {
      this.status = 'idle';
      const err = new Error(perm.error || 'Location permission denied');
      this.callbacks.onError?.(err);
      this.callbacks.onStatusChange?.('idle');
      throw err;
    }

    // Reset and start journey on backend
    await resetJourneyBackend(this.journeyId);
    await startJourneyBackend(this.journeyId);

    this.status = 'running';
    this.callbacks.onStatusChange?.('running');

    try {
      // Immediate initial position ping
      const initialPos = await Location.getCurrentPositionAsync({
        accuracy: Location.Accuracy.Balanced,
      });

      const initialCoords: LocationCoordinates = {
        latitude: initialPos.coords.latitude,
        longitude: initialPos.coords.longitude,
        accuracy: initialPos.coords.accuracy ?? null,
        heading: initialPos.coords.heading ?? null,
        speed: initialPos.coords.speed ?? null,
      };

      this.lastSentCoords = initialCoords;
      this.lastSentTime = Date.now();
      this.callbacks.onLocationUpdate?.(initialCoords);
      await this.processJourneyPing(initialCoords);

      // Setup continuous tracking subscription targeting ~20s interval
      await this.startWatchSubscription();
    } catch (err: any) {
      this.stopJourney();
      const error = new Error(err?.message || 'Failed to start GPS journey tracking');
      this.callbacks.onError?.(error);
      throw error;
    }
  }

  /**
   * Pauses an active journey: stops sending backend pings while preserving journey context.
   */
  public async pauseJourney(): Promise<void> {
    if (this.status !== 'running') {
      return;
    }

    this.status = 'paused';
    this.callbacks.onStatusChange?.('paused');
    await pauseJourneyBackend(this.journeyId);
  }

  /**
   * Resumes a paused journey: restarts GPS updates and pings backend.
   */
  public async resumeJourney(): Promise<void> {
    if (this.status !== 'paused') {
      return;
    }

    this.status = 'running';
    this.callbacks.onStatusChange?.('running');
    await resumeJourneyBackend(this.journeyId);

    // If subscription was dropped or needs refresh, ensure it is active
    if (!this.subscription) {
      await this.startWatchSubscription();
    }

    // Immediate location refresh upon resume
    try {
      const pos = await Location.getCurrentPositionAsync({
        accuracy: Location.Accuracy.Balanced,
      });
      const coords: LocationCoordinates = {
        latitude: pos.coords.latitude,
        longitude: pos.coords.longitude,
        accuracy: pos.coords.accuracy ?? null,
      };
      this.lastSentCoords = coords;
      this.lastSentTime = Date.now();
      this.callbacks.onLocationUpdate?.(coords);
      await this.processJourneyPing(coords);
    } catch (err) {
      console.warn('Failed to get fresh position on resume:', err);
    }
  }

  /**
   * Stops continuous location tracking and releases the GPS subscription.
   */
  public async stopJourney(): Promise<void> {
    if (this.subscription) {
      try {
        this.subscription.remove();
      } catch (e) {
        console.warn('Error removing location subscription:', e);
      }
      this.subscription = null;
    }

    const previousStatus = this.status;
    this.status = 'stopped';
    this.lastSentCoords = null;
    this.lastSentTime = 0;
    this.isPingInProgress = false;

    if (previousStatus !== 'stopped') {
      this.callbacks.onStatusChange?.('stopped');
      await stopJourneyBackend(this.journeyId);
    }
  }

  private async startWatchSubscription(): Promise<void> {
    if (this.subscription) {
      try {
        this.subscription.remove();
      } catch {}
      this.subscription = null;
    }

    this.subscription = await Location.watchPositionAsync(
      {
        accuracy: Location.Accuracy.Balanced,
        timeInterval: 20000, // 20 seconds target
        distanceInterval: 50, // 50 meters
      },
      async (location) => {
        if (this.status !== 'running') {
          return;
        }

        // Stale location check (> 60s old or inaccurate)
        const isStale = location.timestamp && (Date.now() - location.timestamp > 60000);
        if (isStale) {
          loggerDebug('Ignored stale GPS location reading');
          return;
        }

        const coords: LocationCoordinates = {
          latitude: location.coords.latitude,
          longitude: location.coords.longitude,
          accuracy: location.coords.accuracy ?? null,
          heading: location.coords.heading ?? null,
          speed: location.coords.speed ?? null,
        };

        // Duplicate prevention: avoid flooding backend if movement < 15m and elapsed < 15s
        if (this.lastSentCoords) {
          const dist = calculateDistance(
            this.lastSentCoords.latitude,
            this.lastSentCoords.longitude,
            coords.latitude,
            coords.longitude
          );
          const elapsedMs = Date.now() - this.lastSentTime;

          if (dist < 15 && elapsedMs < 15000) {
            return;
          }
        }

        this.lastSentCoords = coords;
        this.lastSentTime = Date.now();
        this.callbacks.onLocationUpdate?.(coords);
        await this.processJourneyPing(coords);
      }
    );
  }

  private async processJourneyPing(coords: LocationCoordinates): Promise<void> {
    if (this.isPingInProgress || this.status !== 'running') {
      return;
    }

    this.isPingInProgress = true;
    try {
      const response = await sendJourneyLocation({
        journeyId: this.journeyId,
        latitude: coords.latitude,
        longitude: coords.longitude,
        timestamp: new Date().toISOString(),
        autoNarrationEnabled: this.autoNarrationEnabled,
        heading: coords.heading ?? null,
        speed: coords.speed ?? null,
        destination: this.destination,
      });

      if (this.status !== 'running') {
        return;
      }

      this.callbacks.onJourneyResponse?.(response);
    } catch (err: any) {
      if (this.status === 'running') {
        console.warn('Journey location ping failed:', err?.message);
        this.callbacks.onError?.(err instanceof Error ? err : new Error(String(err)));
      }
    } finally {
      this.isPingInProgress = false;
    }
  }
}

function calculateDistance(lat1: number, lon1: number, lat2: number, lon2: number): number {
  const R = 6371000;
  const dLat = ((lat2 - lat1) * Math.PI) / 180;
  const dLon = ((lon2 - lon1) * Math.PI) / 180;
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos((lat1 * Math.PI) / 180) *
      Math.cos((lat2 * Math.PI) / 180) *
      Math.sin(dLon / 2) *
      Math.sin(dLon / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return R * c;
}

function loggerDebug(msg: string) {
  if (__DEV__) {
    console.log('[JourneyTracking]', msg);
  }
}

export const journeyTracker = new JourneyTrackingManager();
