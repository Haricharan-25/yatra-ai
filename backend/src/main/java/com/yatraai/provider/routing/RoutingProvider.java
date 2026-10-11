package com.yatraai.provider.routing;

import com.yatraai.model.RouteInfo;

public interface RoutingProvider {
    RouteInfo calculateRoute(double startLat, double startLon, double destLat, double destLon, String destinationName);
}
