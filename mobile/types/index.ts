export interface LocationCoordinates {
  latitude: number;
  longitude: number;
  accuracy: number | null;
  heading?: number | null;
  speed?: number | null;
}

export interface LocationResponse {
  latitude?: number;
  longitude?: number;
  city: string;
  state: string;
  country: string;
  displayName: string;
}

export interface StoryRequest {
  city: string;
  state?: string;
  country?: string;
  targetPlace?: Place;
  weather?: Weather;
  triggerReason?: JourneyEvent;
  previousStories?: string[];
  destination?: string;
}

export interface StoryResponse {
  city: string;
  story: string;
  durationSeconds: number;
  placeName?: string;
  category?: string;
  triggerReason?: JourneyEvent;
}

export interface Place {
  id: string;
  name: string;
  category: string;
  description?: string;
  latitude: number;
  longitude: number;
  distanceMeters: number;
  importanceScore: number;
}

export interface Weather {
  temperatureCelsius: number;
  condition: string;
  humidityPercent: number;
  windSpeedKmh: number;
}

export type JourneyEvent =
  | 'ROUTE_UPCOMING_LANDMARK'
  | 'ROUTE_UPCOMING_ATTRACTION'
  | 'CITY_ENTERED'
  | 'CITY_CHANGED'
  | 'NEARBY_MAJOR_LANDMARK'
  | 'NEARBY_ATTRACTION'
  | 'REGION_CHANGED'
  | 'NONE';

export interface RoutePoint {
  latitude: number;
  longitude: number;
}

export interface RouteInfo {
  destinationName: string;
  destinationLat?: number | null;
  destinationLon?: number | null;
  totalDistanceMeters: number;
  remainingDistanceMeters: number;
  progressPercent: number;
  geometry?: RoutePoint[];
  nextUpcomingPlace?: Place | null;
  distanceToNextPlaceMeters?: number | null;
  isNextPlaceUpcoming?: boolean | null;
  summary?: string;
}

export interface JourneyLocationRequest {
  journeyId?: string;
  latitude: number;
  longitude: number;
  timestamp?: string;
  autoNarrationEnabled?: boolean;
  heading?: number | null;
  speed?: number | null;
  destination?: string | null;
  destinationLat?: number | null;
  destinationLon?: number | null;
}

export interface JourneyLocationResponse {
  journeyId?: string;
  city: string;
  state: string;
  country: string;
  displayName: string;
  nearbyPlaces: Place[];
  topPlace: Place | null;
  weather: Weather | null;
  triggerStory: boolean;
  narrationRecommended?: boolean;
  triggerReason: JourneyEvent;
  detectedEvents?: JourneyEvent[];
  story: string | null;
  durationSeconds: number;
  routeInfo?: RouteInfo | null;
  upcomingRoutePlace?: Place | null;
}

export type JourneyStatus = 'idle' | 'running' | 'paused' | 'stopped';

export type AppStatus =
  | 'idle'
  | 'detecting-location'
  | 'finding-city'
  | 'preparing-story'
  | 'journey-active'
  | 'journey-paused'
  | 'journey-stopped'
  | 'ready'
  | 'permission-denied'
  | 'error';

export interface LocationPermissionResult {
  granted: boolean;
  canAskAgain?: boolean;
  error?: string;
}
