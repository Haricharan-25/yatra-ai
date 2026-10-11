package com.yatraai;

import com.yatraai.model.LocationResponse;
import com.yatraai.provider.geocoding.GeocodingProvider;
import com.yatraai.service.GeocodingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;

class GeocodingServiceTest {

    @Test
    @DisplayName("Reverse geocoding with null coordinates returns graceful fallback without throwing")
    void testNullCoordinatesFallback() {
        GeocodingProvider mockProvider = Mockito.mock(GeocodingProvider.class);
        when(mockProvider.reverseGeocode(null, null))
                .thenReturn(new LocationResponse(null, null, "Unknown Location", "", "", "Coordinates not provided"));

        GeocodingService service = new GeocodingService(mockProvider);
        LocationResponse response = service.reverseGeocode(null, null);

        assertNotNull(response);
        assertEquals("Unknown Location", response.city());
    }

    @Test
    @DisplayName("Reverse geocoding with coordinates returns a non-null LocationResponse with latitude and longitude")
    void testReverseGeocodeReturnsResponse() {
        GeocodingProvider mockProvider = Mockito.mock(GeocodingProvider.class);
        when(mockProvider.reverseGeocode(anyDouble(), anyDouble()))
                .thenReturn(new LocationResponse(17.3616, 78.4747, "Hyderabad", "Telangana", "India", "Hyderabad, Telangana, India"));

        GeocodingService service = new GeocodingService(mockProvider);
        LocationResponse response = service.reverseGeocode(17.3616, 78.4747);

        assertNotNull(response);
        assertEquals(17.3616, response.latitude());
        assertEquals(78.4747, response.longitude());
        assertEquals("Hyderabad", response.city());
        assertEquals("Telangana", response.state());
        assertEquals("India", response.country());
        assertNotNull(response.displayName());
    }
}
