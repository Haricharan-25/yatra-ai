package com.yatraai.controller;

import com.yatraai.model.LocationRequest;
import com.yatraai.model.LocationResponse;
import com.yatraai.service.GeocodingService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/location")
public class LocationController {

    private static final Logger logger = LoggerFactory.getLogger(LocationController.class);

    private final GeocodingService geocodingService;

    public LocationController(GeocodingService geocodingService) {
        this.geocodingService = geocodingService;
    }

    @PostMapping("/reverse-geocode")
    public ResponseEntity<LocationResponse> reverseGeocode(@Valid @RequestBody LocationRequest request) {
        logger.info("Received reverse geocode request: lat={}, lon={}", request.latitude(), request.longitude());
        LocationResponse response = geocodingService.reverseGeocode(request.latitude(), request.longitude());
        return ResponseEntity.ok(response);
    }
}
