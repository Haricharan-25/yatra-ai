package com.yatraai.model;

public record Place(
    String id,
    String name,
    String category,
    String description,
    double latitude,
    double longitude,
    double distanceMeters,
    int importanceScore
) {}
