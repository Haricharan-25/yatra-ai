package com.yatraai.model;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * In-memory representation of an active or paused journey session.
 */
public class JourneyState {

    public enum Status {
        ACTIVE,
        PAUSED,
        STOPPED
    }

    private final String journeyId;
    private Status status;
    private final Instant startTime;
    private Instant lastUpdateTime;
    private String previousCity;
    private String previousState;
    private String previousCountry;
    private Instant lastNarrationTime;
    private static final int MAX_PREVIOUS_STORIES = 10;
    private static final int MAX_NARRATED_PLACES = 100;

    private Double lastLatitude;
    private Double lastLongitude;
    private Double latestHeading;
    private Double latestSpeed;

    private String destination;
    private Double destinationLat;
    private Double destinationLon;
    private RouteInfo currentRoute;

    private final Set<String> narratedPlaceIds = Collections.synchronizedSet(new java.util.LinkedHashSet<>());
    private final Set<String> narratedPlaceNames = Collections.synchronizedSet(new java.util.LinkedHashSet<>());
    private final List<String> previousStories = Collections.synchronizedList(new ArrayList<>());

    public JourneyState(String journeyId) {
        this.journeyId = journeyId != null && !journeyId.isBlank() ? journeyId : "default";
        this.status = Status.ACTIVE;
        this.startTime = Instant.now();
        this.lastUpdateTime = this.startTime;
    }

    public synchronized String getJourneyId() {
        return journeyId;
    }

    public synchronized Status getStatus() {
        return status;
    }

    public synchronized void setStatus(Status status) {
        this.status = status;
        this.lastUpdateTime = Instant.now();
    }

    public synchronized boolean isActive() {
        return this.status == Status.ACTIVE;
    }

    public synchronized boolean isPaused() {
        return this.status == Status.PAUSED;
    }

    public synchronized boolean isStopped() {
        return this.status == Status.STOPPED;
    }

    public synchronized void pause() {
        this.status = Status.PAUSED;
        this.lastUpdateTime = Instant.now();
    }

    public synchronized void resume() {
        this.status = Status.ACTIVE;
        this.lastUpdateTime = Instant.now();
    }

    public synchronized void stop() {
        this.status = Status.STOPPED;
        this.lastUpdateTime = Instant.now();
    }

    public synchronized Instant getStartTime() {
        return startTime;
    }

    public synchronized Instant getLastUpdateTime() {
        return lastUpdateTime;
    }

    public synchronized void setLastUpdateTime(Instant lastUpdateTime) {
        this.lastUpdateTime = lastUpdateTime;
    }

    public synchronized String getPreviousCity() {
        return previousCity;
    }

    public synchronized void setPreviousCity(String previousCity) {
        this.previousCity = previousCity;
    }

    public synchronized String getPreviousState() {
        return previousState;
    }

    public synchronized void setPreviousState(String previousState) {
        this.previousState = previousState;
    }

    public synchronized String getPreviousCountry() {
        return previousCountry;
    }

    public synchronized void setPreviousCountry(String previousCountry) {
        this.previousCountry = previousCountry;
    }

    public synchronized Instant getLastNarrationTime() {
        return lastNarrationTime;
    }

    public synchronized void setLastNarrationTime(Instant lastNarrationTime) {
        this.lastNarrationTime = lastNarrationTime;
    }

    public synchronized Double getLastLatitude() {
        return lastLatitude;
    }

    public synchronized Double getLastLongitude() {
        return lastLongitude;
    }

    public synchronized Double getLatestHeading() {
        return latestHeading;
    }

    public synchronized void setLatestHeading(Double latestHeading) {
        this.latestHeading = latestHeading;
    }

    public synchronized Double getLatestSpeed() {
        return latestSpeed;
    }

    public synchronized void setLatestSpeed(Double latestSpeed) {
        this.latestSpeed = latestSpeed;
    }

    public synchronized void updateCoordinates(double latitude, double longitude) {
        this.lastLatitude = latitude;
        this.lastLongitude = longitude;
        this.lastUpdateTime = Instant.now();
    }

    public synchronized void updateMotion(double latitude, double longitude, Double heading, Double speed) {
        this.lastLatitude = latitude;
        this.lastLongitude = longitude;
        this.latestHeading = heading;
        this.latestSpeed = speed;
        this.lastUpdateTime = Instant.now();
    }

    public synchronized boolean hasRoute() {
        return this.currentRoute != null && this.currentRoute.geometry() != null && !this.currentRoute.geometry().isEmpty();
    }

    public synchronized RouteInfo getCurrentRoute() {
        return currentRoute;
    }

    public synchronized void setCurrentRoute(RouteInfo currentRoute) {
        this.currentRoute = currentRoute;
        if (currentRoute != null) {
            this.destination = currentRoute.destinationName();
            this.destinationLat = currentRoute.destinationLat();
            this.destinationLon = currentRoute.destinationLon();
        }
        this.lastUpdateTime = Instant.now();
    }

    public synchronized void clearRoute() {
        this.currentRoute = null;
        this.destination = null;
        this.destinationLat = null;
        this.destinationLon = null;
        this.lastUpdateTime = Instant.now();
    }

    public synchronized String getDestination() {
        return destination;
    }

    public synchronized void setDestination(String destination, Double lat, Double lon) {
        this.destination = destination;
        this.destinationLat = lat;
        this.destinationLon = lon;
        this.lastUpdateTime = Instant.now();
    }

    public synchronized Double getDestinationLat() {
        return destinationLat;
    }

    public synchronized Double getDestinationLon() {
        return destinationLon;
    }

    public synchronized Set<String> getNarratedPlaceIds() {
        return new HashSet<>(narratedPlaceIds);
    }

    public synchronized Set<String> getNarratedPlaceNames() {
        return new HashSet<>(narratedPlaceNames);
    }

    public synchronized List<String> getPreviousStories() {
        return new ArrayList<>(previousStories);
    }

    public synchronized boolean hasNarratedPlace(Place place) {
        if (place == null) return false;
        if (place.id() != null && narratedPlaceIds.contains(place.id())) return true;
        if (place.name() != null && narratedPlaceNames.contains(place.name().toLowerCase().trim())) return true;
        return false;
    }

    public synchronized void recordNarration(String city, String state, Place place, String storyText, Instant narrationTime) {
        if (city != null && !city.isBlank()) {
            this.previousCity = city.trim();
        }
        if (state != null && !state.isBlank()) {
            this.previousState = state.trim();
        }
        this.lastNarrationTime = narrationTime != null ? narrationTime : Instant.now();
        this.lastUpdateTime = this.lastNarrationTime;

        if (place != null) {
            if (place.id() != null) {
                narratedPlaceIds.add(place.id());
                if (narratedPlaceIds.size() > MAX_NARRATED_PLACES) {
                    var it = narratedPlaceIds.iterator();
                    if (it.hasNext()) { it.next(); it.remove(); }
                }
            }
            if (place.name() != null) {
                narratedPlaceNames.add(place.name().toLowerCase().trim());
                if (narratedPlaceNames.size() > MAX_NARRATED_PLACES) {
                    var it = narratedPlaceNames.iterator();
                    if (it.hasNext()) { it.next(); it.remove(); }
                }
            }
        }

        if (storyText != null && !storyText.isBlank()) {
            previousStories.add(storyText);
            if (previousStories.size() > MAX_PREVIOUS_STORIES) {
                previousStories.remove(0);
            }
        }
    }

    public synchronized long getTimeSinceLastStorySeconds(Instant referenceTime) {
        if (lastNarrationTime == null) {
            return Long.MAX_VALUE;
        }
        Instant ref = referenceTime != null ? referenceTime : Instant.now();
        return Math.max(0, Duration.between(lastNarrationTime, ref).getSeconds());
    }

    public synchronized void reset() {
        this.previousCity = null;
        this.previousState = null;
        this.previousCountry = null;
        this.lastNarrationTime = null;
        this.lastLatitude = null;
        this.lastLongitude = null;
        this.latestHeading = null;
        this.latestSpeed = null;
        this.currentRoute = null;
        this.destination = null;
        this.destinationLat = null;
        this.destinationLon = null;
        this.narratedPlaceIds.clear();
        this.narratedPlaceNames.clear();
        this.previousStories.clear();
        this.status = Status.ACTIVE;
        this.lastUpdateTime = Instant.now();
    }
}
