package com.yatraai.provider.geocoding;

import com.yatraai.model.LocationPoint;
import com.yatraai.model.LocationResponse;

public interface GeocodingProvider {
    LocationResponse reverseGeocode(Double latitude, Double longitude);

    default LocationPoint searchDestination(String query) {
        return null;
    }
}
