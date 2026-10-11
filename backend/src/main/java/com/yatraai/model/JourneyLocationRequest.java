package com.yatraai.model;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record JourneyLocationRequest(
    String journeyId,

    @NotNull(message = "Latitude is required")
    @DecimalMin(value = "-90.0", message = "Latitude must be >= -90.0")
    @DecimalMax(value = "90.0", message = "Latitude must be <= 90.0")
    Double latitude,

    @NotNull(message = "Longitude is required")
    @DecimalMin(value = "-180.0", message = "Longitude must be >= -180.0")
    @DecimalMax(value = "180.0", message = "Longitude must be <= 180.0")
    Double longitude,

    Instant timestamp,
    Boolean autoNarrationEnabled,
    Double heading,
    Double speed,
    String destination,
    Double destinationLat,
    Double destinationLon
) {
    public JourneyLocationRequest(String journeyId, Double latitude, Double longitude, Instant timestamp, Boolean autoNarrationEnabled) {
        this(journeyId, latitude, longitude, timestamp, autoNarrationEnabled, null, null, null, null, null);
    }

    public JourneyLocationRequest(Double latitude, Double longitude, Instant timestamp, Boolean autoNarrationEnabled) {
        this(null, latitude, longitude, timestamp, autoNarrationEnabled, null, null, null, null, null);
    }
}
