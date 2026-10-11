import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { LocationResponse } from '../types';

interface CityCardProps {
  locationInfo: LocationResponse | null;
}

export const CityCard: React.FC<CityCardProps> = ({ locationInfo }) => {
  const hasCity = Boolean(locationInfo && locationInfo.city);

  return (
    <View style={styles.card}>
      <Text style={styles.sectionHeader}>Detected City</Text>

      {hasCity && locationInfo ? (
        <View style={styles.content}>
          <Text style={styles.cityName}>{locationInfo.city}</Text>

          {(locationInfo.state || locationInfo.country) && (
            <Text style={styles.regionText}>
              {[locationInfo.state, locationInfo.country].filter(Boolean).join(', ')}
            </Text>
          )}

          {locationInfo.displayName && (
            <View style={styles.displayBadge}>
              <Text style={styles.displayBadgeText} numberOfLines={2}>
                {locationInfo.displayName}
              </Text>
            </View>
          )}
        </View>
      ) : (
        <View style={styles.emptyContainer}>
          <Text style={styles.emptyText}>Not detected yet</Text>
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
  sectionHeader: {
    color: '#9FB3C8',
    fontSize: 13,
    fontWeight: '700',
    letterSpacing: 0.8,
    textTransform: 'uppercase',
    marginBottom: 12,
  },
  content: {
    backgroundColor: 'rgba(7, 17, 31, 0.6)',
    borderRadius: 12,
    padding: 16,
    borderWidth: 1,
    borderColor: 'rgba(30, 44, 66, 0.6)',
  },
  cityName: {
    color: '#00D4FF',
    fontSize: 22,
    fontWeight: '700',
    marginBottom: 4,
  },
  regionText: {
    color: '#9FB3C8',
    fontSize: 14,
    marginBottom: 10,
  },
  displayBadge: {
    backgroundColor: 'rgba(10, 132, 255, 0.1)',
    borderColor: 'rgba(10, 132, 255, 0.3)',
    borderWidth: 1,
    borderRadius: 8,
    paddingHorizontal: 10,
    paddingVertical: 6,
  },
  displayBadgeText: {
    color: '#4DA3FF',
    fontSize: 12,
    lineHeight: 16,
  },
  emptyContainer: {
    backgroundColor: 'rgba(7, 17, 31, 0.6)',
    borderRadius: 12,
    padding: 16,
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
});
