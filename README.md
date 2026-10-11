# YatraAI — Location-Aware Travel Intelligence (Phase 2)

**YatraAI** is an AI-powered audio travel companion designed for road trips, commutes, and bus journeys. While travelers listen to music or podcasts, YatraAI acts as an intelligent co-pilot that continuously monitors their journey, detects geographic boundaries, identifies interesting nearby historical and cultural landmarks, blends live weather context, and narrates engaging audio stories in real-time.

---

## Phase 2 Architecture Flow

```text
               Continuous GPS Tracking (Expo Location)
                                 ↓
             Latitude + Longitude (every ~20s / 100m)
                                 ↓
             POST /api/journey/location (Spring Boot)
                                 ↓
          ┌──────────────────────┼──────────────────────┐
          ↓                      ↓                      ↓
  [GeocodingProvider]    [PlacesProvider]       [WeatherProvider]
  OSM Nominatim API      OSM Overpass API       Open-Meteo API
  (City / Region)        (Nearby Landmarks)     (Temp, Weather)
          │                      │                      │
          └──────────────────────┼──────────────────────┘
                                 ↓
                     [StoryTriggerService]
          ┌──────────────────────────────────────────────┐
          │ - Haversine Distance (Landmark <= 2km)       │
          │ - Importance Score (10=Fort, 9=Monument...)  │
          │ - City Change Detection (prevCity != curr)   │
          │ - Duplicate Prevention (In-Memory Narrated)  │
          │ - Narration Cooldown Window (5 minutes)      │
          └──────────────────────┬───────────────────────┘
                                 ↓
                     Should Trigger Story?
                   /                       \
                 YES                        NO
                  ↓                          ↓
         [AiService Context]           Return Context
         (City + POI + Weather)       (Silent Tracking)
                  ↓
          20–40s Audio Story
                  ↓
         [Expo Speech TTS Engine]
       (Auto-narrates if enabled)
```

---

## Key Phase 2 Features

1. **Continuous Location Tracking**:
   - Expo Location watches device position with balanced GPS accuracy.
   - Pings backend every ~20s or upon 100m movement without draining battery or flooding network.
   - Halts all GPS tracking when the journey is stopped.

2. **Point of Interest (POI) Engine**:
   - Discovers historical landmarks, forts, palaces, monuments, temples, museums, and natural features via OpenStreetMap Overpass.
   - Computes distance using the **Haversine formula**.
   - Scores POIs: Major historical landmark (10), monument/temple (9), natural attraction (8), museum (7), cultural location (6), ordinary business (2), utility/ATM/fuel (0).
   - Filters out non-attractions (threshold >= 6).

3. **Story Trigger Engine**:
   - Events: `CITY_ENTERED`, `CITY_CHANGED`, `NEARBY_MAJOR_LANDMARK`, `NEARBY_ATTRACTION`.
   - In-memory duplicate prevention: A place narrated on a journey is never repeated.
   - Configurable cooldown window (default: 5 minutes / 300s).

4. **Weather Context**:
   - Integrates Open-Meteo API for real-time temperature, condition, humidity, and wind speed.
   - Free, reliable, open, and requires no API key.
   - Blended naturally into travel narrations when relevant.

5. **Context-Aware AI Narration**:
   - Receives current city, state, country, top POI, distance, weather, and previously narrated stories.
   - Outputs conversational, plain-text narrations tailored for TTS (no markdown, no bullets).
   - Rich, deterministic fallback generator operates even when `AI_API_KEY` is not set.

6. **Automatic Narration Mode**:
   - Toggle switch in UI: When **ON**, stories play automatically through device speaker via `expo-speech`.
   - When **OFF**, stories update on screen with a manual `[ ▶ Listen ]` button.

---

## Project Structure

```text
yatra-ai/
│
├── mobile/
│   ├── app/
│   │   ├── _layout.tsx                 # Dark travel theme container
│   │   └── index.tsx                   # Phase 2 Journey Screen
│   ├── components/
│   │   ├── CityCard.tsx                # Detected City & Region display
│   │   ├── JourneyControlCard.tsx      # Start/Stop Journey & Auto-narration toggle
│   │   ├── LocationCard.tsx            # GPS Metrics display
│   │   ├── NearbyPlaceCard.tsx         # POI name, category, and Haversine distance
│   │   ├── StatusBanner.tsx            # Live status alerts & error banner
│   │   ├── StoryCard.tsx               # Audio story narration & TTS playback
│   │   └── WeatherCard.tsx             # Live temperature, humidity, condition
│   ├── services/
│   │   ├── apiService.ts               # Journey & Phase 1 REST client
│   │   ├── journeyTrackingService.ts   # Continuous GPS manager & event dispatcher
│   │   ├── locationService.ts          # One-shot GPS & permission service
│   │   └── speechService.ts            # Expo Speech TTS wrapper
│   ├── types/
│   │   └── index.ts                    # TypeScript contracts & interfaces
│   ├── app.json
│   ├── package.json
│   └── tsconfig.json
│
├── backend/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/yatraai/
│   │   │   │   ├── YatraAiApplication.java
│   │   │   │   ├── config/
│   │   │   │   │   ├── CorsConfig.java
│   │   │   │   │   ├── GlobalExceptionHandler.java
│   │   │   │   │   └── RestTemplateConfig.java
│   │   │   │   ├── controller/
│   │   │   │   │   ├── JourneyController.java       # POST /api/journey/location, /reset
│   │   │   │   │   ├── LocationController.java      # POST /api/location/reverse-geocode
│   │   │   │   │   ├── PlacesController.java        # GET /api/places/nearby
│   │   │   │   │   └── StoryController.java         # POST /api/story/generate
│   │   │   │   ├── model/
│   │   │   │   │   ├── CityInfo.java
│   │   │   │   │   ├── JourneyEvent.java
│   │   │   │   │   ├── JourneyLocationRequest.java
│   │   │   │   │   ├── JourneyLocationResponse.java
│   │   │   │   │   ├── LocationContext.java
│   │   │   │   │   ├── LocationPoint.java
│   │   │   │   │   ├── LocationRequest.java
│   │   │   │   │   ├── LocationResponse.java
│   │   │   │   │   ├── Place.java
│   │   │   │   │   ├── StoryRequest.java
│   │   │   │   │   ├── StoryResponse.java
│   │   │   │   │   └── Weather.java
│   │   │   │   ├── provider/
│   │   │   │   │   ├── geocoding/
│   │   │   │   │   │   ├── GeocodingProvider.java
│   │   │   │   │   │   └── NominatimGeocodingProvider.java
│   │   │   │   │   ├── places/
│   │   │   │   │   │   ├── PlacesProvider.java
│   │   │   │   │   │   └── OverpassPlacesProvider.java
│   │   │   │   │   └── weather/
│   │   │   │   │       ├── WeatherProvider.java
│   │   │   │   │       └── OpenMeteoWeatherProvider.java
│   │   │   │   └── service/
│   │   │   │       ├── AiService.java
│   │   │   │       ├── GeocodingService.java
│   │   │   │       ├── JourneyService.java
│   │   │   │       ├── PlacesService.java
│   │   │   │       ├── StoryService.java
│   │   │   │       ├── StoryTriggerService.java
│   │   │   │       └── WeatherService.java
│   │   │   └── resources/
│   │   │       └── application.properties
│   │   └── test/java/com/yatraai/
│   │       ├── GeocodingServiceTest.java
│   │       ├── JourneyControllerTest.java
│   │       ├── JourneyServiceTest.java
│   │       ├── LocationControllerTest.java
│   │       ├── PlacesServiceTest.java
│   │       ├── StoryControllerTest.java
│   │       ├── StoryServiceTest.java
│   │       └── StoryTriggerServiceTest.java
│   └── pom.xml
│
├── .env.example
└── README.md
```

---

## Environment Variables

Copy `.env.example` to `.env`:

```bash
# Nominatim Geocoding
NOMINATIM_URL=https://nominatim.openstreetmap.org/reverse
NOMINATIM_USER_AGENT=YatraAI-TravelCompanion/1.0 (contact@yatraai.local)

# Overpass Places
OVERPASS_URL=https://overpass-api.de/api/interpreter
TRIGGER_DIST_MAJOR_LANDMARK=2000.0
TRIGGER_DIST_NORMAL_ATTRACTION=1000.0
TRIGGER_DIST_SCENIC_FEATURE=1500.0
MIN_IMPORTANCE_THRESHOLD=6

# Weather
WEATHER_URL=https://api.open-meteo.com/v1/forecast

# Story Trigger Cooldown
JOURNEY_STORY_COOLDOWN_SECONDS=300

# Optional External AI API Key
AI_API_KEY=
AI_API_URL=https://api.openai.com/v1/chat/completions
AI_MODEL=gpt-4o-mini

# Mobile Backend Target (mobile/.env)
EXPO_PUBLIC_API_URL=http://localhost:8080
```

---

## Running the Application

### 1. Run the Spring Boot Backend

```bash
cd backend
mvn spring-boot:run
```

Available endpoints:
- `POST /api/journey/location`: Continuous location update with city, places, weather, and trigger decisions.
- `POST /api/journey/reset`: Resets in-memory journey tracking state.
- `GET /api/places/nearby?latitude=...&longitude=...&radius=...`: Discovers ranked POIs.
- `POST /api/location/reverse-geocode`: Phase 1 single-shot reverse geocode.
- `POST /api/story/generate`: Phase 1 story generator.

### 2. Run the Mobile Client

In a separate terminal:

```bash
cd mobile
npm start
```

Press `a` for Android Emulator, `i` for iOS Simulator, or scan the QR code with Expo Go on your phone.

---

## Running Automated Tests

Run all 20 unit and integration tests:

```bash
cd backend
mvn test
```

Tests include:
- `StoryTriggerServiceTest`: City entry, city change detection, narration cooldown, duplicate POI prevention, and state reset.
- `PlacesServiceTest`: Haversine distance accuracy and POI importance scoring (forts=10, monuments=9, temples=9, lakes=8, museums=7, shops=2, ATMs=0).
- `JourneyControllerTest`: REST API validation and response payload verification.
- `JourneyServiceTest`: Fault-tolerance and resilience when external weather or places APIs fail.
- `GeocodingServiceTest` & `LocationControllerTest`: Reverse-geocoding functionality.
- `StoryControllerTest` & `StoryServiceTest`: Story generation and duration estimation.
