package com.web.labportalbackend.common.location;

/**
 * Stateless geographic distance helpers.
 */
public final class GeoDistance {

    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    private GeoDistance() {
    }

    public static double distanceMeters(double latitudeOne, double longitudeOne,
                                        double latitudeTwo, double longitudeTwo) {
        validateCoordinate(latitudeOne, -90.0, 90.0, "latitude");
        validateCoordinate(latitudeTwo, -90.0, 90.0, "latitude");
        validateCoordinate(longitudeOne, -180.0, 180.0, "longitude");
        validateCoordinate(longitudeTwo, -180.0, 180.0, "longitude");

        double latitudeDelta = Math.toRadians(latitudeTwo - latitudeOne);
        double longitudeDelta = Math.toRadians(longitudeTwo - longitudeOne);
        double firstLatitude = Math.toRadians(latitudeOne);
        double secondLatitude = Math.toRadians(latitudeTwo);
        double haversine = Math.pow(Math.sin(latitudeDelta / 2), 2)
                + Math.cos(firstLatitude) * Math.cos(secondLatitude)
                * Math.pow(Math.sin(longitudeDelta / 2), 2);
        double centralAngle = 2 * Math.atan2(Math.sqrt(haversine), Math.sqrt(1 - haversine));
        return EARTH_RADIUS_METERS * centralAngle;
    }

    private static void validateCoordinate(double value, double minimum, double maximum, String name) {
        if (!Double.isFinite(value) || value < minimum || value > maximum) {
            throw new IllegalArgumentException("Invalid " + name + " coordinate");
        }
    }
}
