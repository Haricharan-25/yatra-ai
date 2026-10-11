import React from 'react';
import { View, Text, StyleSheet, TouchableOpacity, Switch, ActivityIndicator } from 'react-native';
import { JourneyStatus } from '../types';

interface JourneyControlCardProps {
  journeyStatus: JourneyStatus;
  autoNarration: boolean;
  onStartJourney: () => void;
  onPauseJourney: () => void;
  onResumeJourney: () => void;
  onStopJourney: () => void;
  onToggleAutoNarration: (value: boolean) => void;
  isLoading: boolean;
  lastPingTime: string | null;
}

export const JourneyControlCard: React.FC<JourneyControlCardProps> = ({
  journeyStatus,
  autoNarration,
  onStartJourney,
  onPauseJourney,
  onResumeJourney,
  onStopJourney,
  onToggleAutoNarration,
  isLoading,
  lastPingTime,
}) => {
  const isRunning = journeyStatus === 'running';
  const isPaused = journeyStatus === 'paused';
  const isInactive = journeyStatus === 'idle' || journeyStatus === 'stopped';

  const getStatusLabel = () => {
    switch (journeyStatus) {
      case 'running':
        return 'TRACKING ACTIVE';
      case 'paused':
        return 'JOURNEY PAUSED';
      case 'stopped':
        return 'JOURNEY STOPPED';
      default:
        return 'READY TO TRAVEL';
    }
  };

  return (
    <View style={styles.card}>
      <View style={styles.headerRow}>
        <View style={styles.statusIndicatorRow}>
          <View
            style={[
              styles.statusDot,
              isRunning && styles.dotActive,
              isPaused && styles.dotPaused,
              isInactive && styles.dotInactive,
            ]}
          />
          <Text
            style={[
              styles.statusText,
              isRunning && styles.textActive,
              isPaused && styles.textPaused,
              isInactive && styles.textInactive,
            ]}
          >
            {getStatusLabel()}
          </Text>
        </View>

        {lastPingTime && isRunning && (
          <Text style={styles.pingText}>Updated {lastPingTime}</Text>
        )}
      </View>

      {/* Lifecycle Action Buttons */}
      {isInactive && (
        <TouchableOpacity
          style={[styles.actionButton, styles.startButton, isLoading && styles.buttonDisabled]}
          onPress={onStartJourney}
          disabled={isLoading}
          activeOpacity={0.8}
        >
          {isLoading ? (
            <ActivityIndicator size="small" color="#FFFFFF" />
          ) : (
            <Text style={styles.actionButtonText}>▶ Start Journey</Text>
          )}
        </TouchableOpacity>
      )}

      {isRunning && (
        <View style={styles.dualButtonRow}>
          <TouchableOpacity
            style={[styles.dualButton, styles.pauseButton, isLoading && styles.buttonDisabled]}
            onPress={onPauseJourney}
            disabled={isLoading}
            activeOpacity={0.8}
          >
            <Text style={styles.pauseButtonText}>⏸ Pause</Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={[styles.dualButton, styles.stopButton, isLoading && styles.buttonDisabled]}
            onPress={onStopJourney}
            disabled={isLoading}
            activeOpacity={0.8}
          >
            <Text style={styles.actionButtonText}>⏹ Stop</Text>
          </TouchableOpacity>
        </View>
      )}

      {isPaused && (
        <View style={styles.dualButtonRow}>
          <TouchableOpacity
            style={[styles.dualButton, styles.resumeButton, isLoading && styles.buttonDisabled]}
            onPress={onResumeJourney}
            disabled={isLoading}
            activeOpacity={0.8}
          >
            {isLoading ? (
              <ActivityIndicator size="small" color="#FFFFFF" />
            ) : (
              <Text style={styles.actionButtonText}>▶ Resume</Text>
            )}
          </TouchableOpacity>

          <TouchableOpacity
            style={[styles.dualButton, styles.stopButton, isLoading && styles.buttonDisabled]}
            onPress={onStopJourney}
            disabled={isLoading}
            activeOpacity={0.8}
          >
            <Text style={styles.actionButtonText}>⏹ Stop</Text>
          </TouchableOpacity>
        </View>
      )}

      {/* Auto Narration Toggle */}
      <View style={styles.toggleRow}>
        <View style={styles.toggleInfo}>
          <Text style={styles.toggleTitle}>Auto Narration</Text>
          <Text style={styles.toggleSubtitle}>
            {autoNarration
              ? 'Automatically plays audio stories when landmarks appear'
              : 'Shows story on screen with manual Listen button'}
          </Text>
        </View>
        <Switch
          value={autoNarration}
          onValueChange={onToggleAutoNarration}
          trackColor={{ false: '#1E2C42', true: '#0A84FF' }}
          thumbColor={autoNarration ? '#00D4FF' : '#9FB3C8'}
        />
      </View>
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
    marginBottom: 14,
  },
  statusIndicatorRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  statusDot: {
    width: 10,
    height: 10,
    borderRadius: 5,
  },
  dotActive: {
    backgroundColor: '#30D158',
  },
  dotPaused: {
    backgroundColor: '#FF9F0A',
  },
  dotInactive: {
    backgroundColor: '#637A9F',
  },
  statusText: {
    fontSize: 12,
    fontWeight: '700',
    letterSpacing: 0.8,
  },
  textActive: {
    color: '#30D158',
  },
  textPaused: {
    color: '#FF9F0A',
  },
  textInactive: {
    color: '#9FB3C8',
  },
  pingText: {
    color: '#637A9F',
    fontSize: 11,
    fontWeight: '500',
  },
  actionButton: {
    borderRadius: 12,
    paddingVertical: 14,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 14,
  },
  startButton: {
    backgroundColor: '#0A84FF',
  },
  dualButtonRow: {
    flexDirection: 'row',
    gap: 12,
    marginBottom: 14,
  },
  dualButton: {
    flex: 1,
    borderRadius: 12,
    paddingVertical: 14,
    alignItems: 'center',
    justifyContent: 'center',
  },
  resumeButton: {
    backgroundColor: '#30D158',
  },
  pauseButton: {
    backgroundColor: '#1E2C42',
    borderWidth: 1,
    borderColor: '#FF9F0A',
  },
  pauseButtonText: {
    color: '#FF9F0A',
    fontSize: 15,
    fontWeight: '700',
  },
  stopButton: {
    backgroundColor: '#FF453A',
  },
  buttonDisabled: {
    opacity: 0.6,
  },
  actionButtonText: {
    color: '#FFFFFF',
    fontSize: 15,
    fontWeight: '700',
  },
  toggleRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingTop: 12,
    borderTopWidth: 1,
    borderTopColor: 'rgba(255, 255, 255, 0.06)',
  },
  toggleInfo: {
    flex: 1,
    paddingRight: 16,
  },
  toggleTitle: {
    color: '#FFFFFF',
    fontSize: 14,
    fontWeight: '600',
  },
  toggleSubtitle: {
    color: '#9FB3C8',
    fontSize: 12,
    marginTop: 2,
    lineHeight: 16,
  },
});
