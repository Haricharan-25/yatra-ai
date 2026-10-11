package com.yatraai.service;

import com.yatraai.model.Weather;
import com.yatraai.provider.weather.WeatherProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class WeatherService {

    private static final Logger logger = LoggerFactory.getLogger(WeatherService.class);

    private final WeatherProvider weatherProvider;

    public WeatherService(WeatherProvider weatherProvider) {
        this.weatherProvider = weatherProvider;
    }

    public Weather getCurrentWeather(double latitude, double longitude) {
        try {
            return weatherProvider.getCurrentWeather(latitude, longitude);
        } catch (Exception e) {
            logger.warn("Could not retrieve weather: {}. Continuing without weather data.", e.getMessage());
            return null;
        }
    }
}
