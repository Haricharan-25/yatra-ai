package com.yatraai;

import com.yatraai.model.Place;
import com.yatraai.provider.places.PlacesProvider;
import com.yatraai.service.PlacesService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlacesServiceTest {

    private final PlacesService placesService = new PlacesService((lat, lon, r) -> Collections.emptyList());

    @Test
    @DisplayName("Haversine distance between Charminar and Golconda Fort is approximately 9-10 km")
    void testHaversineDistance() {
        // Charminar: 17.3616, 78.4747
        // Golconda Fort: 17.3833, 78.4011
        double distance = placesService.calculateHaversineDistance(
                17.3616, 78.4747,
                17.3833, 78.4011
        );

        assertTrue(distance > 8000 && distance < 11000,
                "Distance should be around 8-11 km, was: " + distance);
    }

    @Test
    @DisplayName("Haversine distance between identical coordinates is zero")
    void testZeroDistance() {
        double distance = placesService.calculateHaversineDistance(17.3850, 78.4866, 17.3850, 78.4866);
        assertEquals(0.0, distance, 0.001);
    }

    @Test
    @DisplayName("POI importance score assignments match specification")
    void testPoiImportanceScoring() {
        Place fort = new Place("1", "Golconda Fort", "fort_palace", "", 0, 0, 0, 0);
        Place archSite = new Place("2", "Ancient Ruins", "archaeological_site", "", 0, 0, 0, 0);
        Place monument = new Place("3", "City Memorial Monument", "monument", "", 0, 0, 0, 0);
        Place temple = new Place("4", "Birla Mandir Temple", "cultural_temple", "", 0, 0, 0, 0);
        Place lake = new Place("5", "Hussain Sagar Lake", "natural_attraction", "", 0, 0, 0, 0);
        Place museum = new Place("6", "Salar Jung Museum", "museum", "", 0, 0, 0, 0);
        Place viewpoint = new Place("7", "Hilltop Viewpoint", "viewpoint", "", 0, 0, 0, 0);
        Place shop = new Place("8", "General Supermarket", "shop", "", 0, 0, 0, 0);
        Place atm = new Place("9", "HDFC Bank ATM", "amenity", "", 0, 0, 0, 0);

        assertEquals(10, placesService.calculateImportanceScore(fort));
        assertEquals(10, placesService.calculateImportanceScore(archSite));
        assertEquals(9, placesService.calculateImportanceScore(monument));
        assertEquals(9, placesService.calculateImportanceScore(temple));
        assertEquals(8, placesService.calculateImportanceScore(lake));
        assertEquals(7, placesService.calculateImportanceScore(museum));
        assertEquals(6, placesService.calculateImportanceScore(viewpoint));
        assertEquals(2, placesService.calculateImportanceScore(shop));
        assertEquals(0, placesService.calculateImportanceScore(atm));
    }

    @Test
    @DisplayName("Empty attraction results handled gracefully")
    void testEmptyAttractionResults() {
        PlacesProvider emptyProvider = (lat, lon, r) -> Collections.emptyList();
        PlacesService service = new PlacesService(emptyProvider);

        List<Place> results = service.getNearbyPlaces(17.3850, 78.4866, 2000.0);
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("Attractions are sorted by importance score descending, then distance ascending")
    void testAttractionPrioritizationAndFiltering() {
        PlacesProvider mockProvider = (lat, lon, r) -> List.of(
                new Place("1", "Local Bakery", "shop", "", 17.3851, 78.4867, 0, 0), // score 2 (filtered out)
                new Place("2", "City Museum", "museum", "", 17.3860, 78.4870, 0, 0), // score 7
                new Place("3", "Royal Palace", "fort_palace", "", 17.3900, 78.4900, 0, 0) // score 10
        );
        PlacesService service = new PlacesService(mockProvider);

        List<Place> results = service.getNearbyPlaces(17.3850, 78.4866, 2500.0);

        assertEquals(2, results.size());
        assertEquals("Royal Palace", results.get(0).name());
        assertEquals(10, results.get(0).importanceScore());
        assertEquals("City Museum", results.get(1).name());
        assertEquals(7, results.get(1).importanceScore());
    }
}
