import { Platform } from 'react-native';
import {
  LocationResponse,
  StoryRequest,
  StoryResponse,
  JourneyLocationRequest,
  JourneyLocationResponse,
  Place,
  RouteInfo,
} from '../types';

/**
 * Resolves the backend base URL.
 * Priority:
 * 1. Environment variable EXPO_PUBLIC_API_URL (configurable in .env)
 * 2. Android emulator loopback host (10.0.2.2:8080)
 * 3. iOS simulator / Web localhost (localhost:8080)
 */
export function getApiBaseUrl(): string {
  const envUrl = process.env.EXPO_PUBLIC_API_URL?.trim();
  if (envUrl) {
    return envUrl.replace(/\/+$/, '');
  }

  if (Platform.OS === 'android' || Platform.OS === 'ios') {
    return 'http://192.168.0.197:8080';
  }

  return 'http://localhost:8080';
};

const REQUEST_TIMEOUT_MS = 15000;

async function fetchWithTimeout(url: string, options: RequestInit = {}): Promise<Response> {
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);

  try {
    const response = await fetch(url, {
      ...options,
      signal: controller.signal,
    });
    return response;
  } catch (error: any) {
    if (error.name === 'AbortError') {
      throw new Error('Connection timed out. The server took too long to respond.');
    }
    throw error;
  } finally {
    clearTimeout(timeoutId);
  }
}

/**
 * Sends a continuous journey location ping to the backend.
 * Returns city info, nearby places, weather, and triggered stories.
 */
export async function sendJourneyLocation(
  request: JourneyLocationRequest
): Promise<JourneyLocationResponse> {
  const baseUrl = getApiBaseUrl();
  const endpoint = `${baseUrl}/api/journey/location`;

  try {
    const response = await fetchWithTimeout(endpoint, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'application/json',
      },
      body: JSON.stringify(request),
    });

    if (!response.ok) {
      let errorMsg = `Server error (${response.status})`;
      try {
        const errJson = await response.json();
        if (errJson?.message) errorMsg = errJson.message;
      } catch { }
      throw new Error(errorMsg);
    }

    const data: JourneyLocationResponse = await response.json();
    return data;
  } catch (error: any) {
    if (
      error.message?.includes('Network request failed') ||
      error.message?.includes('Failed to fetch')
    ) {
      throw new Error(
        `Unable to reach YatraAI backend at ${baseUrl}. Please ensure the server is running.`
      );
    }
    throw error;
  }
}

/**
 * Starts a journey on the backend.
 */
export async function startJourneyBackend(journeyId: string = 'default'): Promise<{ journeyId: string; status: string }> {
  const baseUrl = getApiBaseUrl();
  const endpoint = `${baseUrl}/api/journey/start`;

  try {
    const response = await fetchWithTimeout(endpoint, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify({ journeyId }),
    });
    return await response.json();
  } catch (error: any) {
    console.warn('Could not notify backend of start journey:', error?.message);
    return { journeyId, status: 'ACTIVE' };
  }
}

/**
 * Pauses a journey on the backend.
 */
export async function pauseJourneyBackend(journeyId: string = 'default'): Promise<{ journeyId: string; status: string }> {
  const baseUrl = getApiBaseUrl();
  const endpoint = `${baseUrl}/api/journey/pause`;

  try {
    const response = await fetchWithTimeout(endpoint, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify({ journeyId }),
    });
    return await response.json();
  } catch (error: any) {
    console.warn('Could not notify backend of pause journey:', error?.message);
    return { journeyId, status: 'PAUSED' };
  }
}

/**
 * Resumes a journey on the backend.
 */
export async function resumeJourneyBackend(journeyId: string = 'default'): Promise<{ journeyId: string; status: string }> {
  const baseUrl = getApiBaseUrl();
  const endpoint = `${baseUrl}/api/journey/resume`;

  try {
    const response = await fetchWithTimeout(endpoint, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify({ journeyId }),
    });
    return await response.json();
  } catch (error: any) {
    console.warn('Could not notify backend of resume journey:', error?.message);
    return { journeyId, status: 'ACTIVE' };
  }
}

/**
 * Stops a journey on the backend.
 */
export async function stopJourneyBackend(journeyId: string = 'default'): Promise<{ journeyId: string; status: string }> {
  const baseUrl = getApiBaseUrl();
  const endpoint = `${baseUrl}/api/journey/stop`;

  try {
    const response = await fetchWithTimeout(endpoint, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify({ journeyId }),
    });
    return await response.json();
  } catch (error: any) {
    console.warn('Could not notify backend of stop journey:', error?.message);
    return { journeyId, status: 'STOPPED' };
  }
}

/**
 * Resets the in-memory journey state on the backend.
 */
export async function resetJourneyBackend(journeyId?: string): Promise<{ status: string }> {
  const baseUrl = getApiBaseUrl();
  const endpoint = `${baseUrl}/api/journey/reset`;

  try {
    const response = await fetchWithTimeout(endpoint, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify({ journeyId: journeyId || 'default' }),
    });
    return await response.json();
  } catch (error: any) {
    console.warn('Could not reset backend journey:', error?.message);
    return { status: 'Reset attempted' };
  }
}

/**
 * Fetches nearby places of interest directly.
 */
export async function fetchNearbyPlaces(
  latitude: number,
  longitude: number,
  radius: number = 2500
): Promise<Place[]> {
  const baseUrl = getApiBaseUrl();
  const endpoint = `${baseUrl}/api/places/nearby?latitude=${latitude}&longitude=${longitude}&radius=${radius}`;

  try {
    const response = await fetchWithTimeout(endpoint, {
      method: 'GET',
      headers: { Accept: 'application/json' },
    });
    if (!response.ok) return [];
    return await response.json();
  } catch {
    return [];
  }
}

/**
 * Calls backend reverse geocoding API to identify city, state, and country.
 */
export async function reverseGeocode(
  latitude: number,
  longitude: number
): Promise<LocationResponse> {
  const baseUrl = getApiBaseUrl();
  const endpoint = `${baseUrl}/api/location/reverse-geocode`;

  try {
    const response = await fetchWithTimeout(endpoint, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'application/json',
      },
      body: JSON.stringify({ latitude, longitude }),
    });

    if (!response.ok) {
      let errorMsg = `Server error (${response.status})`;
      try {
        const errJson = await response.json();
        if (errJson?.message) errorMsg = errJson.message;
      } catch { }
      throw new Error(errorMsg);
    }

    return await response.json();
  } catch (error: any) {
    if (
      error.message?.includes('Network request failed') ||
      error.message?.includes('Failed to fetch')
    ) {
      throw new Error(
        `Unable to reach YatraAI backend at ${baseUrl}. Please ensure the server is running.`
      );
    }
    throw error;
  }
}

/**
 * Calls backend story generation API to generate a travel story.
 */
export async function generateStory(request: StoryRequest): Promise<StoryResponse> {
  const baseUrl = getApiBaseUrl();
  const endpoint = `${baseUrl}/api/story/generate`;

  try {
    const response = await fetchWithTimeout(endpoint, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'application/json',
      },
      body: JSON.stringify(request),
    });

    if (!response.ok) {
      let errorMsg = `Failed to generate story (${response.status})`;
      try {
        const errJson = await response.json();
        if (errJson?.message) errorMsg = errJson.message;
      } catch { }
      throw new Error(errorMsg);
    }

    return await response.json();
  } catch (error: any) {
    if (
      error.message?.includes('Network request failed') ||
      error.message?.includes('Failed to fetch')
    ) {
      throw new Error(
        `Unable to reach YatraAI backend at ${baseUrl}. Please verify your network and server connection.`
      );
    }
    throw error;
  }
}

/**
 * Sets a route for the journey with origin and destination.
 */
export async function setJourneyRoute(
  journeyId: string,
  destination: string,
  originLat: number,
  originLon: number,
  destLat?: number,
  destLon?: number
): Promise<RouteInfo | null> {
  const baseUrl = getApiBaseUrl();
  const endpoint = `${baseUrl}/api/journey/route`;

  try {
    const response = await fetchWithTimeout(endpoint, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify({
        journeyId,
        destination,
        originLat,
        originLon,
        destLat,
        destLon,
      }),
    });

    if (!response.ok) {
      let errorMsg = `Route calculation failed (${response.status})`;
      try {
        const errJson = await response.json();
        if (errJson?.error) errorMsg = errJson.error;
      } catch {}
      throw new Error(errorMsg);
    }

    return await response.json();
  } catch (error: any) {
    console.warn('Failed to set journey route:', error?.message);
    throw error;
  }
}

/**
 * Clears the active route for the journey.
 */
export async function clearJourneyRoute(journeyId: string = 'default'): Promise<void> {
  const baseUrl = getApiBaseUrl();
  const endpoint = `${baseUrl}/api/journey/route/clear`;

  try {
    await fetchWithTimeout(endpoint, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify({ journeyId }),
    });
  } catch (error: any) {
    console.warn('Failed to clear journey route:', error?.message);
  }
}

