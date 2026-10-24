package com.nithin.ridebooking.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class GeoBoxTest {

    @Test
    void boxIsRoughlyTheRadiusInEachDirection() {
        GeoBox box = GeoBox.around(12.97, 77.59, 5.0);

        assertThat(box.maxLat() - 12.97).isCloseTo(5.0 / 110.0, within(0.0001));
        assertThat(12.97 - box.minLat()).isCloseTo(5.0 / 110.0, within(0.0001));
        // a degree of longitude is shorter than a degree of latitude, so the box is wider
        assertThat(box.maxLng() - 77.59).isGreaterThan(box.maxLat() - 12.97);
    }

    @Test
    void pointExactlyOneRadiusAwayIsInsideTheBox() {
        double lat = 12.97, lng = 77.59, radiusKm = 5.0;
        GeoBox box = GeoBox.around(lat, lng, radiusKm);

        double east = lng + radiusKm / (111.195 * Math.cos(Math.toRadians(lat)));
        double north = lat + radiusKm / 111.195;

        assertThat(east).isBetween(box.minLng(), box.maxLng());
        assertThat(north).isBetween(box.minLat(), box.maxLat());
    }

    @Test
    void nearThePoleTheBoxStaysFinite() {
        GeoBox box = GeoBox.around(89.99, 10.0, 5.0);

        assertThat(Double.isFinite(box.minLng())).isTrue();
        assertThat(Double.isFinite(box.maxLng())).isTrue();
    }
}