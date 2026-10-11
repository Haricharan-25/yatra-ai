import React from 'react';
import { View, Text, StyleSheet, TouchableOpacity, ActivityIndicator } from 'react-native';
import { StoryResponse } from '../types';
import { NarrationStatus } from '../services/narrationCoordinator';

interface StoryCardProps {
  storyInfo: StoryResponse | null;
  isSpeaking: boolean;
  onListen: () => void;
  onStop: () => void;
  isLoading: boolean;
  narrationStatus?: NarrationStatus;
  narrationMessage?: string | null;
  isJourneyActive?: boolean;
}

export const StoryCard: React.FC<StoryCardProps> = ({
  storyInfo,
  isSpeaking,
  onListen,
  onStop,
  isLoading,
  narrationStatus,
  narrationMessage,
  isJourneyActive = false,
}) => {
  const hasStory = Boolean(storyInfo && storyInfo.story);

  return (
    <View style={styles.card}>
      <View style={styles.headerRow}>
        <Text style={styles.sectionHeader}>Travel Story</Text>
        {hasStory && storyInfo && (
          <View style={styles.badge}>
            <Text style={styles.badgeText}>~{storyInfo.durationSeconds}s narration</Text>
          </View>
        )}
      </View>

      {hasStory && storyInfo ? (
        <View style={styles.content}>
          <Text style={styles.storyText}>{storyInfo.story}</Text>

          {isSpeaking && (
            <View style={styles.speakingIndicator}>
              <View style={styles.pulseDot} />
              <Text style={styles.speakingText}>Playing audio narration...</Text>
            </View>
          )}

          {!isSpeaking && narrationStatus === 'finished' && (
            <View style={[styles.speakingIndicator, styles.finishedIndicator]}>
              <Text style={styles.finishedText}>✓ Narration finished</Text>
            </View>
          )}

          {!isSpeaking && narrationStatus === 'disabled' && (
            <View style={[styles.speakingIndicator, styles.disabledIndicator]}>
              <Text style={styles.disabledText}>Auto narration disabled • Tap Listen to play</Text>
            </View>
          )}

          {!isSpeaking && narrationStatus === 'error' && (
            <View style={[styles.speakingIndicator, styles.errorIndicator]}>
              <Text style={styles.errorText}>
                {narrationMessage || 'Narration playback failed • Tap Listen to retry'}
              </Text>
            </View>
          )}

          <View style={styles.buttonRow}>
            <TouchableOpacity
              style={[
                styles.button,
                styles.listenButton,
                isSpeaking && styles.buttonActive,
              ]}
              onPress={onListen}
              activeOpacity={0.8}
            >
              <Text style={styles.listenButtonText}>▶ Listen</Text>
            </TouchableOpacity>

            <TouchableOpacity
              style={[
                styles.button,
                styles.stopButton,
                !isSpeaking && styles.stopButtonDisabled,
              ]}
              onPress={onStop}
              activeOpacity={0.8}
              disabled={!isSpeaking}
            >
              <Text
                style={[
                  styles.stopButtonText,
                  !isSpeaking && styles.stopButtonTextDisabled,
                ]}
              >
                ■ Stop
              </Text>
            </TouchableOpacity>
          </View>
        </View>
      ) : (
        <View style={styles.emptyContainer}>
          {isLoading ? (
            <View style={styles.loadingBox}>
              <ActivityIndicator size="small" color="#0A84FF" />
              <Text style={styles.loadingText}>Crafting your story...</Text>
            </View>
          ) : isJourneyActive ? (
            <View style={styles.waitingBox}>
              <View style={styles.pulseDot} />
              <Text style={styles.waitingText}>Waiting for an interesting place along your route...</Text>
            </View>
          ) : (
            <Text style={styles.emptyText}>Start a journey to discover travel stories</Text>
          )}
        </View>
      )}
    </View>
  );
};

const styles = StyleSheet.create({
  card: {
    backgroundColor: '#0F1B2D',
    borderRadius: 16,
    padding: 18,
    marginVertical: 8,
    borderWidth: 1,
    borderColor: '#1E2C42',
  },
  headerRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 12,
  },
  sectionHeader: {
    color: '#9FB3C8',
    fontSize: 13,
    fontWeight: '700',
    letterSpacing: 0.8,
    textTransform: 'uppercase',
  },
  badge: {
    backgroundColor: 'rgba(0, 212, 255, 0.12)',
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 6,
    borderWidth: 1,
    borderColor: 'rgba(0, 212, 255, 0.3)',
  },
  badgeText: {
    color: '#00D4FF',
    fontSize: 11,
    fontWeight: '600',
  },
  content: {
    backgroundColor: 'rgba(7, 17, 31, 0.6)',
    borderRadius: 12,
    padding: 16,
    borderWidth: 1,
    borderColor: 'rgba(30, 44, 66, 0.6)',
  },
  storyText: {
    color: '#FFFFFF',
    fontSize: 15,
    lineHeight: 24,
    marginBottom: 16,
  },
  speakingIndicator: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: 'rgba(10, 132, 255, 0.15)',
    paddingVertical: 8,
    paddingHorizontal: 12,
    borderRadius: 8,
    marginBottom: 14,
    borderWidth: 1,
    borderColor: 'rgba(10, 132, 255, 0.3)',
  },
  pulseDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: '#00D4FF',
    marginRight: 8,
  },
  speakingText: {
    color: '#00D4FF',
    fontSize: 13,
    fontWeight: '500',
  },
  buttonRow: {
    flexDirection: 'row',
    gap: 12,
  },
  button: {
    flex: 1,
    paddingVertical: 12,
    paddingHorizontal: 16,
    borderRadius: 10,
    alignItems: 'center',
    justifyContent: 'center',
  },
  listenButton: {
    backgroundColor: '#0A84FF',
  },
  buttonActive: {
    backgroundColor: '#0070DF',
  },
  listenButtonText: {
    color: '#FFFFFF',
    fontSize: 15,
    fontWeight: '700',
  },
  stopButton: {
    backgroundColor: '#1E2C42',
    borderWidth: 1,
    borderColor: '#304463',
  },
  stopButtonDisabled: {
    opacity: 0.4,
  },
  stopButtonText: {
    color: '#FF6B6B',
    fontSize: 15,
    fontWeight: '700',
  },
  stopButtonTextDisabled: {
    color: '#9FB3C8',
  },
  emptyContainer: {
    backgroundColor: 'rgba(7, 17, 31, 0.6)',
    borderRadius: 12,
    padding: 24,
    borderWidth: 1,
    borderColor: 'rgba(30, 44, 66, 0.6)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  emptyText: {
    color: '#9FB3C8',
    fontSize: 15,
    fontStyle: 'italic',
  },
  loadingBox: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
  },
  loadingText: {
    color: '#9FB3C8',
    fontSize: 14,
  },
  finishedIndicator: {
    backgroundColor: 'rgba(48, 209, 88, 0.12)',
    borderColor: 'rgba(48, 209, 88, 0.3)',
  },
  finishedText: {
    color: '#30D158',
    fontSize: 13,
    fontWeight: '500',
  },
  disabledIndicator: {
    backgroundColor: 'rgba(159, 179, 200, 0.1)',
    borderColor: 'rgba(159, 179, 200, 0.25)',
  },
  disabledText: {
    color: '#9FB3C8',
    fontSize: 13,
    fontWeight: '500',
  },
  errorIndicator: {
    backgroundColor: 'rgba(255, 69, 58, 0.12)',
    borderColor: 'rgba(255, 69, 58, 0.3)',
  },
  errorText: {
    color: '#FF453A',
    fontSize: 13,
    fontWeight: '500',
  },
  waitingBox: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  waitingText: {
    color: '#9FB3C8',
    fontSize: 14,
    fontStyle: 'italic',
  },
});
