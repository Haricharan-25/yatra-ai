import React, { useState } from 'react';
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  ActivityIndicator,
  StyleSheet,
} from 'react-native';
import { RouteInfo } from '../types';

interface RouteCardProps {
  routeInfo: RouteInfo | null;
  destination: string;
  isJourneyActive: boolean;
  isLoading: boolean;
  onSetRoute: (destination: string) => Promise<void> | void;
  onClearRoute: () => Promise<void> | void;
}

const PRESET_DESTINATIONS = ['Warangal', 'Golconda Fort', 'Charminar', 'Ramoji City'];

export const RouteCard: React.FC<RouteCardProps> = ({
  routeInfo,
  destination,
  isJourneyActive,
  isLoading,
  onSetRoute,
  onClearRoute,
}) => {
  const [inputText, setInputText] = useState(destination || '');
  const [isEditing, setIsEditing] = useState(!routeInfo);

  const handleApply = () => {
    if (inputText.trim()) {
      onSetRoute(inputText.trim());
      setIsEditing(false);
    }
  };

  const handleClear = () => {
    setInputText('');
    setIsEditing(true);
    onClearRoute();
  };

  const handlePresetSelect = (preset: string) => {
    setInputText(preset);
    onSetRoute(preset);
    setIsEditing(false);
  };

  const formatDistance = (meters: number): string => {
    if (meters < 1000) {
      return `${Math.round(meters)} m`;
    }
    return `${(meters / 1000).toFixed(1)} km`;
  };

  const nextPlace = routeInfo?.nextUpcomingPlace;
  const isUpcoming = routeInfo?.isNextPlaceUpcoming ?? true;
  const distToNext = routeInfo?.distanceToNextPlaceMeters;

  return (
    <View style={styles.card}>
      {/* Header */}
      <View style={styles.headerRow}>
        <View style={styles.titleContainer}>
          <Text style={styles.routeIcon}>🛣️</Text>
          <Text style={styles.sectionHeader}>Route & Destination</Text>
        </View>

        {routeInfo && (
          <TouchableOpacity
            style={styles.clearButton}
            onPress={handleClear}
            activeOpacity={0.7}
          >
            <Text style={styles.clearButtonText}>✕ Clear</Text>
          </TouchableOpacity>
        )}
      </View>

      {/* When Route is Active */}
      {routeInfo && !isEditing ? (
        <View style={styles.activeRouteContainer}>
          {/* Destination Badge & Summary */}
          <View style={styles.destinationRow}>
            <View style={styles.destBadge}>
              <Text style={styles.destLabel}>DESTINATION</Text>
              <Text style={styles.destName}>{routeInfo.destinationName}</Text>
            </View>
            {routeInfo.summary ? (
              <Text style={styles.summaryText}>{routeInfo.summary}</Text>
            ) : null}
          </View>

          {/* Progress Bar */}
          <View style={styles.progressContainer}>
            <View style={styles.progressBarBackground}>
              <View
                style={[
                  styles.progressBarFill,
                  { width: `${Math.min(100, Math.max(0, routeInfo.progressPercent))}%` },
                ]}
              />
            </View>
            <View style={styles.progressStatsRow}>
              <Text style={styles.progressPercentText}>
                {Math.round(routeInfo.progressPercent)}% completed
              </Text>
              <Text style={styles.remainingDistanceText}>
                {formatDistance(routeInfo.remainingDistanceMeters)} remaining
              </Text>
            </View>
          </View>

          {/* Next Upcoming or Passed Attraction */}
          {nextPlace ? (
            <View style={styles.nextPlaceContainer}>
              <View style={styles.nextPlaceHeaderRow}>
                <View
                  style={[
                    styles.statusBadge,
                    isUpcoming ? styles.statusBadgeUpcoming : styles.statusBadgePassed,
                  ]}
                >
                  <Text
                    style={[
                      styles.statusBadgeText,
                      isUpcoming
                        ? styles.statusBadgeTextUpcoming
                        : styles.statusBadgeTextPassed,
                    ]}
                  >
                    {isUpcoming ? '📍 Upcoming Ahead' : '✓ Recently Passed'}
                  </Text>
                </View>
                {distToNext !== null && distToNext !== undefined && (
                  <Text style={styles.distToNextText}>
                    {formatDistance(distToNext)} {isUpcoming ? 'ahead' : 'behind'}
                  </Text>
                )}
              </View>

              <Text style={styles.nextPlaceName}>{nextPlace.name}</Text>
              <Text style={styles.nextPlaceCategory}>
                {nextPlace.category.replace(/_/g, ' ').toUpperCase()} •{' '}
                {nextPlace.importanceScore >= 9 ? 'Major Landmark' : 'Attraction'}
              </Text>

              {nextPlace.description ? (
                <Text style={styles.nextPlaceDesc} numberOfLines={2}>
                  {nextPlace.description}
                </Text>
              ) : null}
            </View>
          ) : (
            <View style={styles.noPlaceCorridor}>
              <Text style={styles.noPlaceCorridorText}>
                {isJourneyActive
                  ? '🔍 Scanning route corridor for upcoming historical & cultural sights...'
                  : 'Start journey tracking to discover attractions along this route.'}
              </Text>
            </View>
          )}

          {/* Button to edit destination */}
          <TouchableOpacity
            style={styles.changeDestLink}
            onPress={() => setIsEditing(true)}
            activeOpacity={0.7}
          >
            <Text style={styles.changeDestLinkText}>Change Destination</Text>
          </TouchableOpacity>
        </View>
      ) : (
        /* Destination Input Form */
        <View style={styles.inputContainer}>
          <Text style={styles.instructionText}>
            Enter an optional destination to discover attractions ahead along your road route:
          </Text>

          <View style={styles.inputRow}>
            <TextInput
              style={styles.input}
              placeholder="e.g. Warangal, Golconda, Agra"
              placeholderTextColor="#64748B"
              value={inputText}
              onChangeText={setInputText}
              onSubmitEditing={handleApply}
              returnKeyType="done"
              autoCapitalize="words"
            />
            <TouchableOpacity
              style={[
                styles.applyButton,
                (!inputText.trim() || isLoading) && styles.applyButtonDisabled,
              ]}
              onPress={handleApply}
              disabled={!inputText.trim() || isLoading}
              activeOpacity={0.8}
            >
              {isLoading ? (
                <ActivityIndicator size="small" color="#07111F" />
              ) : (
                <Text style={styles.applyButtonText}>Plan Route</Text>
              )}
            </TouchableOpacity>
          </View>

          {/* Preset Chips */}
          <View style={styles.presetsRow}>
            <Text style={styles.presetsLabel}>Quick picks:</Text>
            {PRESET_DESTINATIONS.map((preset) => (
              <TouchableOpacity
                key={preset}
                style={styles.presetChip}
                onPress={() => handlePresetSelect(preset)}
                activeOpacity={0.7}
              >
                <Text style={styles.presetChipText}>{preset}</Text>
              </TouchableOpacity>
            ))}
          </View>
        </View>
      )}
    </View>
  );
};

const styles = StyleSheet.create({
  card: {
    backgroundColor: '#0D1F35',
    borderRadius: 14,
    padding: 16,
    marginBottom: 16,
    borderWidth: 1,
    borderColor: '#1E293B',
  },
  headerRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 12,
  },
  titleContainer: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  routeIcon: {
    fontSize: 16,
    marginRight: 6,
  },
  sectionHeader: {
    color: '#00D4FF',
    fontSize: 14,
    fontWeight: '700',
    letterSpacing: 0.5,
    textTransform: 'uppercase',
  },
  clearButton: {
    backgroundColor: '#1E293B',
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 6,
  },
  clearButtonText: {
    color: '#94A3B8',
    fontSize: 11,
    fontWeight: '600',
  },
  activeRouteContainer: {
    marginTop: 2,
  },
  destinationRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 12,
  },
  destBadge: {
    flex: 1,
  },
  destLabel: {
    color: '#64748B',
    fontSize: 10,
    fontWeight: '700',
    letterSpacing: 0.5,
  },
  destName: {
    color: '#F8FAFC',
    fontSize: 18,
    fontWeight: '700',
    marginTop: 2,
  },
  summaryText: {
    color: '#00D4FF',
    fontSize: 12,
    fontWeight: '600',
  },
  progressContainer: {
    marginBottom: 14,
  },
  progressBarBackground: {
    height: 6,
    backgroundColor: '#1E293B',
    borderRadius: 3,
    overflow: 'hidden',
  },
  progressBarFill: {
    height: '100%',
    backgroundColor: '#00D4FF',
    borderRadius: 3,
  },
  progressStatsRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginTop: 6,
  },
  progressPercentText: {
    color: '#94A3B8',
    fontSize: 11,
    fontWeight: '500',
  },
  remainingDistanceText: {
    color: '#94A3B8',
    fontSize: 11,
    fontWeight: '600',
  },
  nextPlaceContainer: {
    backgroundColor: '#07111F',
    borderRadius: 10,
    padding: 12,
    borderWidth: 1,
    borderColor: '#1E3A8A',
    marginBottom: 8,
  },
  nextPlaceHeaderRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 6,
  },
  statusBadge: {
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 6,
  },
  statusBadgeUpcoming: {
    backgroundColor: '#064E3B',
  },
  statusBadgePassed: {
    backgroundColor: '#334155',
  },
  statusBadgeText: {
    fontSize: 11,
    fontWeight: '700',
  },
  statusBadgeTextUpcoming: {
    color: '#34D399',
  },
  statusBadgeTextPassed: {
    color: '#94A3B8',
  },
  distToNextText: {
    color: '#00D4FF',
    fontSize: 12,
    fontWeight: '700',
  },
  nextPlaceName: {
    color: '#F8FAFC',
    fontSize: 16,
    fontWeight: '700',
    marginBottom: 2,
  },
  nextPlaceCategory: {
    color: '#94A3B8',
    fontSize: 11,
    fontWeight: '600',
    letterSpacing: 0.3,
  },
  nextPlaceDesc: {
    color: '#CBD5E1',
    fontSize: 12,
    marginTop: 4,
    lineHeight: 16,
  },
  noPlaceCorridor: {
    backgroundColor: '#07111F',
    padding: 10,
    borderRadius: 8,
    marginBottom: 8,
  },
  noPlaceCorridorText: {
    color: '#64748B',
    fontSize: 12,
    fontStyle: 'italic',
    textAlign: 'center',
  },
  changeDestLink: {
    alignSelf: 'center',
    paddingVertical: 4,
  },
  changeDestLinkText: {
    color: '#00D4FF',
    fontSize: 12,
    fontWeight: '600',
  },
  inputContainer: {
    marginTop: 2,
  },
  instructionText: {
    color: '#94A3B8',
    fontSize: 12,
    lineHeight: 17,
    marginBottom: 10,
  },
  inputRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 10,
  },
  input: {
    flex: 1,
    backgroundColor: '#07111F',
    color: '#F8FAFC',
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 10,
    fontSize: 14,
    borderWidth: 1,
    borderColor: '#334155',
    marginRight: 8,
  },
  applyButton: {
    backgroundColor: '#00D4FF',
    paddingHorizontal: 14,
    paddingVertical: 11,
    borderRadius: 8,
    alignItems: 'center',
    justifyContent: 'center',
  },
  applyButtonDisabled: {
    backgroundColor: '#334155',
    opacity: 0.7,
  },
  applyButtonText: {
    color: '#07111F',
    fontSize: 13,
    fontWeight: '700',
  },
  presetsRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    alignItems: 'center',
    marginTop: 4,
  },
  presetsLabel: {
    color: '#64748B',
    fontSize: 11,
    marginRight: 6,
  },
  presetChip: {
    backgroundColor: '#1E293B',
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 6,
    marginRight: 6,
    marginBottom: 4,
  },
  presetChipText: {
    color: '#94A3B8',
    fontSize: 11,
    fontWeight: '500',
  },
});
