import * as Location from 'expo-location';
import { LocationCoordinates, LocationPermissionResult } from '../types';

/**
 * Requests foreground location permissions from the user.
 * Returns whether permission was granted or a friendly error description.
 */
export async function requestLocationPermission(): Promise<LocationPermissionResult> {
  try {
    const { status, canAskAgain } = await Location.requestForegroundPermissionsAsync();

    if (status !== Location.PermissionStatus.GRANTED) {
      return {
        granted: false,
        canAskAgain,
        error:
          'Location permission was denied. Please allow location access in your device settings to detect your travel position.',
      };
    }

    return { granted: true, canAskAgain };
  } catch (error: any) {
    return {
      granted: false,
      error: `Could not request location permission: ${error?.message || 'Unknown error'}`,
    };
  }
}

/**
 * Retrieves current GPS coordinates using balanced accuracy.
 * Handles disabled location services, timeouts, and hardware issues gracefully.
 */
export async function getCurrentLocation(): Promise<LocationCoordinates> {
  const isLocationEnabled = await Location.hasServicesEnabledAsync();
  if (!isLocationEnabled) {
    throw new Error('GPS / Location services are turned off. Please turn on location services on your device.');
  }

  try {
    const location = await Location.getCurrentPositionAsync({
      accuracy: Location.Accuracy.Balanced,
    });

    return {
      latitude: location.coords.latitude,
      longitude: location.coords.longitude,
      accuracy: location.coords.accuracy ?? null,
    };
  } catch (error: any) {
    // If standard position times out or fails, try last known position as fallback
    try {
      const lastKnown = await Location.getLastKnownPositionAsync();
      if (lastKnown) {
        return {
          latitude: lastKnown.coords.latitude,
          longitude: lastKnown.coords.longitude,
          accuracy: lastKnown.coords.accuracy ?? null,
        };
      }
    } catch {
      // Ignore fallback error and throw main error below
    }

    throw new Error(
      error?.message || 'Unable to retrieve your current location. Please verify that GPS has a clear signal.'
    );
  }
}
