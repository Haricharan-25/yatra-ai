import * as Speech from 'expo-speech';

export interface SpeakOptions {
  onStart?: () => void;
  onDone?: () => void;
  onStopped?: () => void;
  onError?: (error: Error) => void;
  rate?: number;
  pitch?: number;
  language?: string;
}

/**
 * Checks if the speech synthesizer is currently speaking.
 */
export async function isSpeaking(): Promise<boolean> {
  try {
    return await Speech.isSpeakingAsync();
  } catch {
    return false;
  }
}

/**
 * Stops any active speech playback immediately.
 */
export async function stopStory(): Promise<void> {
  try {
    await Speech.stop();
  } catch (error) {
    console.warn('Error while stopping speech playback:', error);
  }
}

/**
 * Narrates the travel story aloud using Expo Speech.
 */
export function speakStory(text: string, options: SpeakOptions = {}): void {
  if (!text || text.trim().length === 0) {
    return;
  }

  // Stop any active narration before starting new narration
  Speech.stop()
    .catch(() => {})
    .finally(() => {
      try {
        Speech.speak(text.trim(), {
          language: options.language || 'en',
          rate: options.rate ?? 0.95, // comfortable travel narration pace
          pitch: options.pitch ?? 1.0,
          onStart: () => {
            options.onStart?.();
          },
          onDone: () => {
            options.onDone?.();
          },
          onStopped: () => {
            options.onStopped?.();
          },
          onError: (err: any) => {
            options.onError?.(new Error(err?.message || 'Speech playback failed'));
          },
        });
      } catch (err: any) {
        options.onError?.(new Error(err?.message || 'Failed to start text-to-speech'));
      }
    });
}
