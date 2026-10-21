package com.nithin.ridebooking.service;

import com.nithin.ridebooking.dto.FareEstimate;
import com.nithin.ridebooking.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class FareServiceTest {

    // base 50, 12 per km, 1.5 per minute, minimum 80, road factor 1.3, 30 km/h
    private FareService serviceWithSurge(String surge) {
        return new FareService(new BigDecimal("50"), new BigDecimal("12"), new BigDecimal("1.5"),
                new BigDecimal("80"), new BigDecimal(surge), 1.3, 30.0);
    }

    // One degree of latitude along a meridian is exactly 111.19 km, so the expected
    // numbers below can be checked by hand.
    @Test
    void oneDegreeNorthTripMatchesHandCalculation() {
        FareEstimate fare = serviceWithSurge("1.0").estimate(0.0, 77.0, 1.0, 77.0);

        assertThat(fare.distanceKm()).isCloseTo(144.55, within(0.001));   // 111.19 x 1.3
        assertThat(fare.durationMinutes()).isEqualTo(290);                // at 30 km/h, rounded up
        assertThat(fare.baseFare()).isEqualByComparingTo("50.00");
        assertThat(fare.distanceCharge()).isEqualByComparingTo("1734.60"); // 12 x 144.55
        assertThat(fare.timeCharge()).isEqualByComparingTo("435.00");      // 1.5 x 290
        assertThat(fare.total()).isEqualByComparingTo("2219.60");
    }

    @Test
    void shortTripIsChargedTheMinimumFare() {
        FareEstimate fare = serviceWithSurge("1.0").estimate(12.9716, 77.5946, 12.9716, 77.5946);

        assertThat(fare.durationMinutes()).isZero();
        assertThat(fare.total()).isEqualByComparingTo("80.00");
    }

    @Test
    void surgeMultiplierScalesTheFare() {
        FareEstimate fare = serviceWithSurge("1.5").estimate(0.0, 77.0, 1.0, 77.0);

        assertThat(fare.total()).isEqualByComparingTo("3329.40");   // 2219.60 x 1.5
    }

    @Test
    void breakdownAddsUpToTheTotal() {
        FareEstimate fare = serviceWithSurge("1.0").estimate(0.0, 77.0, 1.0, 77.0);

        BigDecimal sum = fare.baseFare().add(fare.distanceCharge()).add(fare.timeCharge());
        assertThat(sum).isEqualByComparingTo(fare.total());
    }

    @Test
    void invalidCoordinatesAreRejected() {
        assertThatThrownBy(() -> serviceWithSurge("1.0").estimate(95.0, 0.0, 0.0, 0.0))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid coordinates");
    }
}