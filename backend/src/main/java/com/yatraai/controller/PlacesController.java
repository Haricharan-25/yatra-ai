package com.yatraai.controller;

import com.yatraai.model.Place;
import com.yatraai.service.PlacesService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/places")
public class PlacesController {

    private static final Logger logger = LoggerFactory.getLogger(PlacesController.class);

    private final PlacesService placesService;

    public PlacesController(PlacesService placesService) {
        this.placesService = placesService;
    }

    @GetMapping("/nearby")
    public ResponseEntity<List<Place>> getNearbyPlaces(
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam(defaultValue = "2500") double radius
    ) {
        logger.info("Nearby places query: lat={}, lon={}, radius={}", latitude, longitude, radius);
        List<Place> places = placesService.getNearbyPlaces(latitude, longitude, radius);
        return ResponseEntity.ok(places);
    }
}
