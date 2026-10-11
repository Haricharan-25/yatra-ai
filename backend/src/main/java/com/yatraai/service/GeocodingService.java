package com.yatraai.service;

import com.yatraai.model.LocationPoint;
import com.yatraai.model.LocationResponse;
import com.yatraai.provider.geocoding.GeocodingProvider;
import org.springframework.stereotype.Service;

@Service
public class GeocodingService {

    private final GeocodingProvider geocodingProvider;

    public GeocodingService(GeocodingProvider geocodingProvider) {
        this.geocodingProvider = geocodingProvider;
    }

    public LocationResponse reverseGeocode(Double latitude, Double longitude) {
        return geocodingProvider.reverseGeocode(latitude, longitude);
    }

    public LocationPoint searchDestination(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        return geocodingProvider.searchDestination(query.trim());
    }
}
