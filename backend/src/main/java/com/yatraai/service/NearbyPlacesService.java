package com.yatraai.service;

import com.yatraai.model.NearbyPlace;
import com.yatraai.model.Place;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NearbyPlacesService {

    private final PlacesService placesService;

    public NearbyPlacesService(PlacesService placesService) {
        this.placesService = placesService;
    }

    public List<Place> getNearbyPlaces(double userLat, double userLon, double searchRadiusMeters) {
        return placesService.getNearbyPlaces(userLat, userLon, searchRadiusMeters);
    }

    public List<NearbyPlace> getNearbyDiscoveredPlaces(double userLat, double userLon, double searchRadiusMeters) {
        return placesService.getNearbyPlaces(userLat, userLon, searchRadiusMeters)
                .stream()
                .map(NearbyPlace::fromPlace)
                .toList();
    }
}
