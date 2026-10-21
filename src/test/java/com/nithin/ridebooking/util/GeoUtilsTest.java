package com.nithin.ridebooking.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class GeoUtilsTest {

    @Test
    void samePointHasZeroDistance() {
        assertThat(GeoUtils.distanceKm(12.9716, 77.5946, 12.9716, 77.5946))
                .isCloseTo(0.0, within(0.0001));
    }

    @Test
    void delhiToMumbaiIsAbout1150Km() {
        double km = GeoUtils.distanceKm(28.6139, 77.2090, 19.0760, 72.8777);
        assertThat(km).isCloseTo(1150.0, within(15.0));
    }

    @Test
    void shortCityTripIsAboutFiveKm() {
        double km = GeoUtils.distanceKm(12.9716, 77.5946, 12.9352, 77.6245);
        assertThat(km).isCloseTo(5.2, within(0.1));
    }

    @Test
    void distanceIsSymmetric() {
        double ab = GeoUtils.distanceKm(12.9716, 77.5946, 13.1000, 77.7000);
        double ba = GeoUtils.distanceKm(13.1000, 77.7000, 12.9716, 77.5946);
        assertThat(ab).isCloseTo(ba, within(1e-9));
    }

    @Test
    void validCoordinatesAreAccepted() {
        assertThat(GeoUtils.isValidCoordinate(0, 0)).isTrue();
        assertThat(GeoUtils.isValidCoordinate(90, 180)).isTrue();
        assertThat(GeoUtils.isValidCoordinate(-90, -180)).isTrue();
    }

    @Test
    void invalidCoordinatesAreRejected() {
        assertThat(GeoUtils.isValidCoordinate(91, 0)).isFalse();
        assertThat(GeoUtils.isValidCoordinate(0, -181)).isFalse();
    }
}