import React, { useState, useEffect } from 'react';
import {
  StyleSheet,
  Text,
  View,
  ScrollView,
  SafeAreaView,
  StatusBar,
} from 'react-native';
import {
  LocationCoordinates,
  LocationResponse,
  StoryResponse,
  Place,
  Weather,
  AppStatus,
  JourneyStatus,
  JourneyLocationResponse,
  RouteInfo,
} from '../types';
import { journeyTracker } from '../services/journeyTrackingService';
import {
  narrationCoordinator,
  NarrationStatus,
} from '../services/narrationCoordinator';
import { setJourneyRoute, clearJourneyRoute } from '../services/apiService';
import { LocationCard } from '../components/LocationCard';
import { CityCard } from '../components/CityCard';
import { StoryCard } from '../components/StoryCard';
import { StatusBanner } from '../components/StatusBanner';
import { JourneyControlCard } from '../components/JourneyControlCard';
import { NearbyPlaceCard } from '../components/NearbyPlaceCard';
import { WeatherCard } from '../components/WeatherCard';
import { RouteCard } from '../components/RouteCard';

export default function IndexScreen() {
  const [status, setStatus] = useState<AppStatus>('idle');
  const [journeyStatus, setJourneyStatus] = useState<JourneyStatus>('idle');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const [autoNarration, setAutoNarration] = useState<boolean>(true);
  const [isJourneyLoading, setIsJourneyLoading] = useState<boolean>(false);

  const [coordinates, setCoordinates] = useState<LocationCoordinates | null>(null);
  const [locationInfo, setLocationInfo] = useState<LocationResponse | null>(null);
  const [nearbyPlaces, setNearbyPlaces] = useState<Place[]>([]);
  const [topPlace, setTopPlace] = useState<Place | null>(null);
  const [weather, setWeather] = useState<Weather | null>(null);
  const [storyInfo, setStoryInfo] = useState<StoryResponse | null>(null);

  // Route-aware storytelling state
  const [destination, setDestination] = useState<string>('');
  const [routeInfo, setRouteInfo] = useState<RouteInfo | null>(null);
  const [isRouteLoading, setIsRouteLoading] = useState<boolean>(false);

  const [isSpeakingState, setIsSpeakingState] = useState<boolean>(false);
  const [narrationStatus, setNarrationStatus] = useState<NarrationStatus>('idle');
  const [narrationMessage, setNarrationMessage] = useState<string | null>(null);
  const [lastPingTime, setLastPingTime] = useState<string | null>(null);

  useEffect(() => {
    const unsubscribe = narrationCoordinator.subscribe({
      onStatusChange: (newStatus, msg) => {
        setNarrationStatus(newStatus);
        setNarrationMessage(msg || null);
      },
      onStoryChange: (story) => {
        setStoryInfo(story);
      },
      onSpeakingChange: (speaking) => {
        setIsSpeakingState(speaking);
      },
    });

    return () => {
      unsubscribe();
      narrationCoordinator.stop();
      journeyTracker.stopJourney();
    };
  }, []);

  const handleToggleAutoNarration = (value: boolean) => {
    setAutoNarration(value);
    journeyTracker.setAutoNarration(value);
    narrationCoordinator.setAutoNarration(value);
  };

  const handleSetRoute = async (newDestination: string) => {
    if (!newDestination.trim()) return;
    setDestination(newDestination.trim());
    journeyTracker.setDestination(newDestination.trim());
    setIsRouteLoading(true);
    setErrorMessage(null);

    try {
      const originLat = coordinates?.latitude || 17.3850;
      const originLon = coordinates?.longitude || 78.4866;
      const route = await setJourneyRoute(
        journeyTracker.getJourneyId(),
        newDestination.trim(),
        originLat,
        originLon
      );
      if (route) {
        setRouteInfo(route);
      }
    } catch (err: any) {
      console.warn('Could not establish route:', err?.message);
      // Non-blocking: show notice but allow journey to continue
      setErrorMessage(`Could not establish route for "${newDestination}": ${err?.message || 'Check connection'}. Continuing with nearby landmarks.`);
    } finally {
      setIsRouteLoading(false);
    }
  };

  const handleClearRoute = async () => {
    setDestination('');
    setRouteInfo(null);
    journeyTracker.setDestination(null);
    try {
      await clearJourneyRoute(journeyTracker.getJourneyId());
    } catch (e) {
      console.warn('Failed to clear route on backend:', e);
    }
  };

  const handleStartJourney = async () => {
    setErrorMessage(null);
    setIsJourneyLoading(true);
    narrationCoordinator.start();

    try {
      await journeyTracker.startJourney({
        onLocationUpdate: (coords) => {
          setCoordinates(coords);
          const now = new Date();
          setLastPingTime(now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' }));
        },
        onJourneyResponse: (res: JourneyLocationResponse) => {
          setIsJourneyLoading(false);
          setLocationInfo({
            latitude: coordsOrFallback(res, coordinates)?.latitude,
            longitude: coordsOrFallback(res, coordinates)?.longitude,
            city: res.city,
            state: res.state,
            country: res.country,
            displayName: res.displayName,
          });
          setNearbyPlaces(res.nearbyPlaces || []);
          setTopPlace(res.topPlace || null);
          setWeather(res.weather || null);

          // Update route info if provided in response
          if (res.routeInfo) {
            setRouteInfo(res.routeInfo);
          }

          // Handle automatic story trigger through narration coordinator
          narrationCoordinator.handleJourneyResponse(res);
        },
        onError: (err) => {
          setIsJourneyLoading(false);
          setErrorMessage(err.message || 'Error occurred during journey tracking.');
          if (err.message?.includes('permission')) {
            setStatus('permission-denied');
          } else {
            setStatus('error');
          }
        },
        onStatusChange: (newStatus) => {
          setJourneyStatus(newStatus);
          if (newStatus === 'running') {
            setStatus('journey-active');
          } else if (newStatus === 'paused') {
            setStatus('journey-paused');
          } else if (newStatus === 'stopped') {
            setStatus('journey-stopped');
          } else {
            setStatus('idle');
          }
        },
      });
    } catch (err: any) {
      setIsJourneyLoading(false);
      setErrorMessage(err?.message || 'Failed to start journey tracking.');
      if (err?.message?.includes('permission')) {
        setStatus('permission-denied');
      } else {
        setStatus('error');
      }
    }
  };

  const handlePauseJourney = async () => {
    setIsJourneyLoading(true);
    try {
      await narrationCoordinator.pause();
      await journeyTracker.pauseJourney();
      setJourneyStatus('paused');
      setStatus('journey-paused');
    } finally {
      setIsJourneyLoading(false);
    }
  };

  const handleResumeJourney = async () => {
    setIsJourneyLoading(true);
    setErrorMessage(null);
    try {
      narrationCoordinator.resume();
      await journeyTracker.resumeJourney();
      setJourneyStatus('running');
      setStatus('journey-active');
    } catch (err: any) {
      setErrorMessage(err?.message || 'Failed to resume journey.');
    } finally {
      setIsJourneyLoading(false);
    }
  };

  const handleStopJourney = async () => {
    setIsJourneyLoading(true);
    try {
      await narrationCoordinator.stop();
      await journeyTracker.stopJourney();
      setJourneyStatus('stopped');
      setStatus('journey-stopped');
    } finally {
      setIsJourneyLoading(false);
    }
  };

  const handleListen = () => {
    if (!storyInfo || !storyInfo.story) return;
    narrationCoordinator.speakManually(storyInfo.story, storyInfo);
  };

  const handleStop = async () => {
    await narrationCoordinator.stopSpeaking();
  };

  return (
    <SafeAreaView style={styles.safeArea}>
      <StatusBar barStyle="light-content" backgroundColor="#07111F" />
      <ScrollView
        contentContainerStyle={styles.scrollContent}
        showsVerticalScrollIndicator={false}
      >
        {/* Header */}
        <View style={styles.header}>
          <Text style={styles.title}>YatraAI</Text>
          <Text style={styles.subtitle}>Location-Aware Travel Intelligence</Text>
        </View>

        {/* Phase 2 Journey Control Card (Start, Pause, Resume, Stop) */}
        <JourneyControlCard
          journeyStatus={journeyStatus}
          autoNarration={autoNarration}
          onStartJourney={handleStartJourney}
          onPauseJourney={handlePauseJourney}
          onResumeJourney={handleResumeJourney}
          onStopJourney={handleStopJourney}
          onToggleAutoNarration={handleToggleAutoNarration}
          isLoading={isJourneyLoading}
          lastPingTime={lastPingTime}
        />

        {/* Status / Error Banner */}
        <StatusBanner status={status} errorMessage={errorMessage} />

        {/* Route-Aware Card (Optional Destination, Route Progress, Upcoming Sights) */}
        <RouteCard
          routeInfo={routeInfo}
          destination={destination}
          isJourneyActive={journeyStatus === 'running'}
          isLoading={isRouteLoading}
          onSetRoute={handleSetRoute}
          onClearRoute={handleClearRoute}
        />

        {/* Nearby Discovered POI Card */}
        <NearbyPlaceCard
          topPlace={topPlace}
          nearbyPlaces={nearbyPlaces}
          isTracking={journeyStatus === 'running'}
        />

        {/* Weather Context Card */}
        <WeatherCard weather={weather} />

        {/* Current Story Card with Audio Playback Controls */}
        <StoryCard
          storyInfo={storyInfo}
          isSpeaking={isSpeakingState}
          onListen={handleListen}
          onStop={handleStop}
          isLoading={isJourneyLoading && !storyInfo}
          narrationStatus={narrationStatus}
          narrationMessage={narrationMessage}
          isJourneyActive={journeyStatus === 'running'}
        />

        {/* Detected City Section */}
        <CityCard locationInfo={locationInfo} />

        {/* Current GPS Coordinates Card */}
        <LocationCard coordinates={coordinates} />
      </ScrollView>
    </SafeAreaView>
  );
}

function coordsOrFallback(res: JourneyLocationResponse, current: LocationCoordinates | null) {
  if (current) return current;
  return null;
}

const styles = StyleSheet.create({
  safeArea: {
    flex: 1,
    backgroundColor: '#07111F',
  },
  scrollContent: {
    paddingHorizontal: 20,
    paddingTop: 20,
    paddingBottom: 40,
  },
  header: {
    alignItems: 'center',
    marginBottom: 16,
  },
  title: {
    color: '#00D4FF',
    fontSize: 32,
    fontWeight: '800',
    letterSpacing: 0.5,
  },
  subtitle: {
    color: '#9FB3C8',
    fontSize: 14,
    marginTop: 2,
    fontWeight: '400',
  },
});
