package com.yatraai.provider.places;

import com.yatraai.model.Place;
import java.util.List;

public interface PlacesProvider {
    List<Place> findNearbyPlaces(double latitude, double longitude, double radiusMeters);
}
