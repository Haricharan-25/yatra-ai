import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { Weather } from '../types';

interface WeatherCardProps {
  weather: Weather | null;
}

export const WeatherCard: React.FC<WeatherCardProps> = ({ weather }) => {
  if (!weather) {
    return null;
  }

  return (
    <View style={styles.card}>
      <View style={styles.headerRow}>
        <Text style={styles.sectionHeader}>Travel Weather</Text>
        <Text style={styles.conditionText}>{weather.condition}</Text>
      </View>

      <View style={styles.metricsRow}>
        <View style={styles.metric}>
          <Text style={styles.label}>Temp</Text>
          <Text style={styles.value}>{Math.round(weather.temperatureCelsius)}°C</Text>
        </View>

        <View style={styles.separator} />

        <View style={styles.metric}>
          <Text style={styles.label}>Humidity</Text>
          <Text style={styles.value}>{weather.humidityPercent}%</Text>
        </View>

        <View style={styles.separator} />

        <View style={styles.metric}>
          <Text style={styles.label}>Wind</Text>
          <Text style={styles.value}>{Math.round(weather.windSpeedKmh)} km/h</Text>
        </View>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  card: {
    backgroundColor: '#0F1B2D',
    borderRadius: 16,
    padding: 16,
    marginVertical: 8,
    borderWidth: 1,
    borderColor: '#1E2C42',
  },
  headerRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 10,
  },
  sectionHeader: {
    color: '#9FB3C8',
    fontSize: 12,
    fontWeight: '700',
    letterSpacing: 0.8,
    textTransform: 'uppercase',
  },
  conditionText: {
    color: '#00D4FF',
    fontSize: 13,
    fontWeight: '600',
  },
  metricsRow: {
    flexDirection: 'row',
    backgroundColor: 'rgba(7, 17, 31, 0.6)',
    borderRadius: 10,
    paddingVertical: 10,
    paddingHorizontal: 12,
    alignItems: 'center',
    justifyContent: 'space-around',
    borderWidth: 1,
    borderColor: 'rgba(30, 44, 66, 0.6)',
  },
  metric: {
    alignItems: 'center',
  },
  label: {
    color: '#9FB3C8',
    fontSize: 11,
    marginBottom: 2,
  },
  value: {
    color: '#FFFFFF',
    fontSize: 14,
    fontWeight: '700',
  },
  separator: {
    width: 1,
    height: 24,
    backgroundColor: 'rgba(30, 44, 66, 0.6)',
  },
});
