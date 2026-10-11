package com.yatraai.controller;

import com.yatraai.model.JourneyLocationRequest;
import com.yatraai.model.JourneyLocationResponse;
import com.yatraai.model.JourneyState;
import com.yatraai.model.LocationContext;
import com.yatraai.model.RouteInfo;
import com.yatraai.service.JourneyService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/journey")
public class JourneyController {

    private static final Logger logger = LoggerFactory.getLogger(JourneyController.class);

    private final JourneyService journeyService;

    public JourneyController(JourneyService journeyService) {
        this.journeyService = journeyService;
    }

    @PostMapping("/location")
    public ResponseEntity<JourneyLocationResponse> updateLocation(@Valid @RequestBody JourneyLocationRequest request) {
        logger.info("Journey location ping: lat={}, lon={}, heading={}, speed={}, journeyId={}",
                request.latitude(), request.longitude(), request.heading(), request.speed(), request.journeyId());
        JourneyLocationResponse response = journeyService.processLocation(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/route")
    public ResponseEntity<Map<String, Object>> setRoute(@RequestBody Map<String, Object> body) {
        String journeyId = body.get("journeyId") != null ? body.get("journeyId").toString() : "default";
        String destination = body.get("destination") != null ? body.get("destination").toString() : "";
        double originLat = body.get("originLat") != null ? Double.parseDouble(body.get("originLat").toString()) : 0.0;
        double originLon = body.get("originLon") != null ? Double.parseDouble(body.get("originLon").toString()) : 0.0;

        Double destLat = body.get("destLat") != null ? Double.parseDouble(body.get("destLat").toString()) : null;
        Double destLon = body.get("destLon") != null ? Double.parseDouble(body.get("destLon").toString()) : null;

        if (destination.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Destination is required"));
        }

        RouteInfo route = journeyService.setJourneyRoute(journeyId, destination, destLat, destLon, originLat, originLon);
        if (route == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Could not calculate route for destination: " + destination));
        }

        return ResponseEntity.ok(Map.of(
                "journeyId", journeyId,
                "destination", route.destinationName(),
                "totalDistanceMeters", route.totalDistanceMeters(),
                "remainingDistanceMeters", route.remainingDistanceMeters(),
                "progressPercent", route.progressPercent(),
                "summary", route.summary() != null ? route.summary() : ""
        ));
    }

    @PostMapping("/route/clear")
    public ResponseEntity<Map<String, String>> clearRoute(@RequestBody(required = false) Map<String, String> body) {
        String journeyId = body != null && body.get("journeyId") != null ? body.get("journeyId") : "default";
        journeyService.clearJourneyRoute(journeyId);
        return ResponseEntity.ok(Map.of("status", "Route cleared successfully", "journeyId", journeyId));
    }

    @PostMapping("/start")
    public ResponseEntity<Map<String, String>> startJourney(@RequestBody(required = false) Map<String, String> body) {
        String journeyId = body != null ? body.get("journeyId") : "default";
        JourneyState state = journeyService.startJourney(journeyId);
        return ResponseEntity.ok(Map.of(
                "journeyId", state.getJourneyId(),
                "status", state.getStatus().name()
        ));
    }

    @PostMapping("/pause")
    public ResponseEntity<Map<String, String>> pauseJourney(@RequestBody(required = false) Map<String, String> body) {
        String journeyId = body != null ? body.get("journeyId") : "default";
        JourneyState state = journeyService.pauseJourney(journeyId);
        return ResponseEntity.ok(Map.of(
                "journeyId", state.getJourneyId(),
                "status", state.getStatus().name()
        ));
    }

    @PostMapping("/resume")
    public ResponseEntity<Map<String, String>> resumeJourney(@RequestBody(required = false) Map<String, String> body) {
        String journeyId = body != null ? body.get("journeyId") : "default";
        JourneyState state = journeyService.resumeJourney(journeyId);
        return ResponseEntity.ok(Map.of(
                "journeyId", state.getJourneyId(),
                "status", state.getStatus().name()
        ));
    }

    @PostMapping("/stop")
    public ResponseEntity<Map<String, String>> stopJourney(@RequestBody(required = false) Map<String, String> body) {
        String journeyId = body != null ? body.get("journeyId") : "default";
        JourneyState state = journeyService.stopJourney(journeyId);
        return ResponseEntity.ok(Map.of(
                "journeyId", state.getJourneyId(),
                "status", state.getStatus().name()
        ));
    }

    @PostMapping("/reset")
    public ResponseEntity<Map<String, String>> resetJourney(@RequestBody(required = false) Map<String, String> body) {
        String journeyId = body != null ? body.get("journeyId") : null;
        if (journeyId != null) {
            journeyService.resetJourney(journeyId);
        } else {
            journeyService.resetJourney();
        }
        return ResponseEntity.ok(Map.of("status", "Journey reset successfully"));
    }

    @GetMapping("/context")
    public ResponseEntity<LocationContext> getLocationContext(
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam(required = false) String journeyId
    ) {
        JourneyLocationRequest req = new JourneyLocationRequest(journeyId, latitude, longitude, Instant.now(), true);
        LocationContext context = journeyService.getLocationContext(req);
        return ResponseEntity.ok(context);
    }
}
