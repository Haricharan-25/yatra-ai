package com.yatraai.provider.weather;

import com.yatraai.model.Weather;

public interface WeatherProvider {
    Weather getCurrentWeather(double latitude, double longitude);
}
