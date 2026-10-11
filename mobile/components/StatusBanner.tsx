import React from 'react';
import { View, Text, StyleSheet, ActivityIndicator } from 'react-native';
import { AppStatus } from '../types';

interface StatusBannerProps {
  status: AppStatus;
  errorMessage?: string | null;
}

export const StatusBanner: React.FC<StatusBannerProps> = ({ status, errorMessage }) => {
  if (status === 'idle') {
    return null;
  }

  if ((status === 'error' || status === 'permission-denied') && errorMessage) {
    return (
      <View style={[styles.container, styles.errorContainer]}>
        <View style={styles.errorDot} />
        <View style={styles.textContainer}>
          <Text style={styles.errorTitle}>
            {status === 'permission-denied' ? 'Location Permission Required' : 'Attention'}
          </Text>
          <Text style={styles.errorMessage}>{errorMessage}</Text>
        </View>
      </View>
    );
  }

  const getStatusText = (): string => {
    switch (status) {
      case 'detecting-location':
        return 'Detecting your location...';
      case 'finding-city':
        return 'Finding your city...';
      case 'preparing-story':
        return 'Preparing your travel story...';
      case 'journey-active':
        return 'Live GPS Tracking Active';
      case 'journey-paused':
        return 'Journey Paused';
      case 'journey-stopped':
        return 'Journey Stopped';
      case 'ready':
        return 'Story ready';
      default:
        return '';
    }
  };

  const isWorking =
    status === 'detecting-location' ||
    status === 'finding-city' ||
    status === 'preparing-story';

  return (
    <View style={[styles.container, isWorking ? styles.workingContainer : styles.readyContainer]}>
      {isWorking && <ActivityIndicator size="small" color="#00D4FF" style={styles.spinner} />}
      {!isWorking && <View style={styles.successDot} />}
      <Text style={[styles.statusText, isWorking ? styles.workingText : styles.readyText]}>
        {getStatusText()}
      </Text>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 12,
    paddingHorizontal: 16,
    borderRadius: 12,
    marginVertical: 10,
    borderWidth: 1,
  },
  workingContainer: {
    backgroundColor: '#0F1B2D',
    borderColor: '#1E2C42',
  },
  readyContainer: {
    backgroundColor: 'rgba(0, 212, 255, 0.08)',
    borderColor: '#00D4FF',
  },
  errorContainer: {
    backgroundColor: 'rgba(255, 69, 58, 0.12)',
    borderColor: 'rgba(255, 69, 58, 0.4)',
  },
  spinner: {
    marginRight: 10,
  },
  successDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: '#00D4FF',
    marginRight: 10,
  },
  errorDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: '#FF453A',
    marginRight: 10,
    marginTop: 4,
  },
  textContainer: {
    flex: 1,
  },
  errorTitle: {
    color: '#FF453A',
    fontSize: 13,
    fontWeight: '700',
    marginBottom: 2,
  },
  errorMessage: {
    color: '#E0E6ED',
    fontSize: 13,
    lineHeight: 18,
  },
  statusText: {
    fontSize: 13,
    fontWeight: '600',
  },
  workingText: {
    color: '#9FB3C8',
  },
  readyText: {
    color: '#00D4FF',
  },
});
