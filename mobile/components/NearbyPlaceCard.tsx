import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { Place } from '../types';

interface NearbyPlaceCardProps {
  topPlace: Place | null;
  nearbyPlaces: Place[];
  isTracking: boolean;
}

export const NearbyPlaceCard: React.FC<NearbyPlaceCardProps> = ({
  topPlace,
  nearbyPlaces,
  isTracking,
}) => {
  const formatDistance = (meters: number): string => {
    if (meters < 1000) {
      return `${Math.round(meters)} m away`;
    }
    return `${(meters / 1000).toFixed(1)} km away`;
  };

  const formatCategory = (cat: string): string => {
    return cat.replace(/_/g, ' ').toUpperCase();
  };

  return (
    <View style={styles.card}>
      <View style={styles.headerRow}>
        <Text style={styles.sectionHeader}>Nearby Interesting Place</Text>
        {topPlace && (
          <View style={styles.badge}>
            <Text style={styles.badgeText}>{formatCategory(topPlace.category)}</Text>
          </View>
        )}
      </View>

      {topPlace ? (
        <View style={styles.content}>
          <View style={styles.topRow}>
            <Text style={styles.placeName}>{topPlace.name}</Text>
            <Text style={styles.distanceText}>{formatDistance(topPlace.distanceMeters)}</Text>
          </View>

          {topPlace.description ? (
            <Text style={styles.description}>{topPlace.description}</Text>
          ) : null}

          {nearbyPlaces.length > 1 && (
            <View style={styles.otherPlacesRow}>
              <Text style={styles.otherPlacesText}>
                +{nearbyPlaces.length - 1} other places detected in vicinity
              </Text>
            </View>
          )}
        </View>
      ) : (
        <View style={styles.emptyContainer}>
          <Text style={styles.emptyText}>
            {isTracking
              ? 'Scanning surroundings for historical & cultural landmarks...'
              : 'Start your journey to discover landmarks along your route'}
          </Text>
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
    fontSize: 10,
    fontWeight: '700',
  },
  content: {
    backgroundColor: 'rgba(7, 17, 31, 0.6)',
    borderRadius: 12,
    padding: 16,
    borderWidth: 1,
    borderColor: 'rgba(30, 44, 66, 0.6)',
  },
  topRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    marginBottom: 6,
  },
  placeName: {
    color: '#FFFFFF',
    fontSize: 18,
    fontWeight: '700',
    flex: 1,
    marginRight: 10,
  },
  distanceText: {
    color: '#00D4FF',
    fontSize: 13,
    fontWeight: '600',
  },
  description: {
    color: '#9FB3C8',
    fontSize: 13,
    lineHeight: 18,
    marginTop: 4,
  },
  otherPlacesRow: {
    marginTop: 10,
    paddingTop: 8,
    borderTopWidth: 1,
    borderTopColor: 'rgba(30, 44, 66, 0.5)',
  },
  otherPlacesText: {
    color: '#4DA3FF',
    fontSize: 12,
    fontWeight: '500',
  },
  emptyContainer: {
    backgroundColor: 'rgba(7, 17, 31, 0.6)',
    borderRadius: 12,
    padding: 18,
    borderWidth: 1,
    borderColor: 'rgba(30, 44, 66, 0.6)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  emptyText: {
    color: '#9FB3C8',
    fontSize: 13,
    fontStyle: 'italic',
    textAlign: 'center',
    lineHeight: 18,
  },
});
