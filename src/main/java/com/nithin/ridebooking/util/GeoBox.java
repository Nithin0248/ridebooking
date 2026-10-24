package com.nithin.ridebooking.util;

/** A rectangle around a point. Cheap to filter on in SQL; the exact circle check happens afterwards. */
public record GeoBox(double minLat, double maxLat, double minLng, double maxLng) {

    // slightly under the real 111.2 km, so the box is always a little bigger than the circle
    private static final double KM_PER_DEGREE = 110.0;

    public static GeoBox around(double lat, double lng, double radiusKm) {
        double latDelta = radiusKm / KM_PER_DEGREE;
        // degrees of longitude get shorter towards the poles; the clamp avoids dividing by ~0
        double cosLat = Math.max(Math.cos(Math.toRadians(lat)), 0.01);
        double lngDelta = radiusKm / (KM_PER_DEGREE * cosLat);
        return new GeoBox(lat - latDelta, lat + latDelta, lng - lngDelta, lng + lngDelta);
    }
}