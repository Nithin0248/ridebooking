package com.nithin.ridebooking.service;

import com.nithin.ridebooking.entity.Driver;
import com.nithin.ridebooking.repository.DriverRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchingServiceTest {

    private static final double PICKUP_LAT = 12.9716;
    private static final double PICKUP_LNG = 77.5946;

    @Mock private DriverRepository driverRepository;

    private MatchingService matchingService;

    @BeforeEach
    void setUp() {
        matchingService = new MatchingService(driverRepository, 5.0);   // 5 km radius
    }

    private Driver driver(long id, Double lat, Double lng) {
        return Driver.builder().id(id).currentLatitude(lat).currentLongitude(lng).build();
    }

    private Driver nearDriver()    { return driver(1L, PICKUP_LAT + 0.005, PICKUP_LNG); }   // about 0.5 km away
    private Driver farDriver()     { return driver(2L, PICKUP_LAT + 0.030, PICKUP_LNG); }   // about 3.3 km away
    private Driver outsideDriver() { return driver(3L, PICKUP_LAT + 0.200, PICKUP_LNG); }   // about 22 km away

    // what the database query returns for the box around the pickup point
    private void databaseReturns(Driver... drivers) {
        when(driverRepository.findAvailableWithin(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(drivers));
    }

    @Test
    void nearestDriverIsClaimedFirst() {
        databaseReturns(farDriver(), nearDriver());      // far one listed first on purpose
        when(driverRepository.claimIfAvailable(1L)).thenReturn(1);

        Optional<Driver> result = matchingService.findAndClaimNearestDriver(PICKUP_LAT, PICKUP_LNG);

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(1L);
        verify(driverRepository, never()).claimIfAvailable(2L);
    }

    @Test
    void nextNearestDriverIsUsedWhenTheNearestWasJustTaken() {
        databaseReturns(nearDriver(), farDriver());
        when(driverRepository.claimIfAvailable(1L)).thenReturn(0);   // someone else claimed it first
        when(driverRepository.claimIfAvailable(2L)).thenReturn(1);

        Optional<Driver> result = matchingService.findAndClaimNearestDriver(PICKUP_LAT, PICKUP_LNG);

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(2L);
    }

    @Test
    void driversOutsideTheRadiusAreIgnored() {
        databaseReturns(outsideDriver());

        Optional<Driver> result = matchingService.findAndClaimNearestDriver(PICKUP_LAT, PICKUP_LNG);

        assertThat(result).isEmpty();
        verify(driverRepository, never()).claimIfAvailable(anyLong());
    }

    @Test
    void noOnlineDriversMeansNoMatch() {
        databaseReturns();

        Optional<Driver> result = matchingService.findAndClaimNearestDriver(PICKUP_LAT, PICKUP_LNG);

        assertThat(result).isEmpty();
    }

    @Test
    void driversWithoutALocationAreIgnored() {
        databaseReturns(driver(4L, null, null));

        Optional<Driver> result = matchingService.findAndClaimNearestDriver(PICKUP_LAT, PICKUP_LNG);

        assertThat(result).isEmpty();
        verify(driverRepository, never()).claimIfAvailable(anyLong());
    }
}