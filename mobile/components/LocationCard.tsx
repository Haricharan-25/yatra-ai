import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { LocationCoordinates } from '../types';

interface LocationCardProps {
  coordinates: LocationCoordinates | null;
}

export const LocationCard: React.FC<LocationCardProps> = ({ coordinates }) => {
  return (
    <View style={styles.card}>
      <Text style={styles.sectionHeader}>Current Location</Text>

      <View style={styles.metricsContainer}>
        <View style={styles.metricRow}>
          <Text style={styles.label}>Latitude</Text>
          <Text style={styles.value}>
            {coordinates ? coordinates.latitude.toFixed(6) : '—'}
          </Text>
        </View>

        <View style={styles.divider} />

        <View style={styles.metricRow}>
          <Text style={styles.label}>Longitude</Text>
          <Text style={styles.value}>
            {coordinates ? coordinates.longitude.toFixed(6) : '—'}
          </Text>
        </View>

        <View style={styles.divider} />

        <View style={styles.metricRow}>
          <Text style={styles.label}>Accuracy</Text>
          <Text style={styles.value}>
            {coordinates && coordinates.accuracy !== null
              ? `± ${Math.round(coordinates.accuracy)} m`
              : '—'}
          </Text>
        </View>
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
  sectionHeader: {
    color: '#9FB3C8',
    fontSize: 13,
    fontWeight: '700',
    letterSpacing: 0.8,
    textTransform: 'uppercase',
    marginBottom: 12,
  },
  metricsContainer: {
    backgroundColor: 'rgba(7, 17, 31, 0.6)',
    borderRadius: 12,
    padding: 14,
    borderWidth: 1,
    borderColor: 'rgba(30, 44, 66, 0.6)',
  },
  metricRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 6,
  },
  divider: {
    height: 1,
    backgroundColor: 'rgba(30, 44, 66, 0.4)',
    marginVertical: 4,
  },
  label: {
    color: '#9FB3C8',
    fontSize: 14,
    fontWeight: '500',
  },
  value: {
    color: '#FFFFFF',
    fontSize: 15,
    fontFamily: 'monospace',
    fontWeight: '600',
  },
});
