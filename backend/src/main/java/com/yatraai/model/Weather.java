package com.yatraai.model;

public record Weather(
    double temperatureCelsius,
    String condition,
    int humidityPercent,
    double windSpeedKmh
) {}
