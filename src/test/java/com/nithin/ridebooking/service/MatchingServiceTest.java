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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MatchingServiceTest {

    private static final double PICKUP_LAT = 12.9716;
    private static final double PICKUP_LNG = 77.5946;

    @Mock
    private DriverRepository driverRepository;

    private MatchingService matchingService;

    @BeforeEach
    void setUp() {
        matchingService = new MatchingService(driverRepository, 5.0);   // 5 km radius
    }

    private Driver driver(Long id, Double lat, Double lng) {
        return Driver.builder().id(id).currentLatitude(lat).currentLongitude(lng)
                .available(true).build();
    }

    @Test
    void nearestDriverIsChosen() {
        Driver near = driver(1L, 12.9720, 77.5950);   // about 0.06 km away
        Driver mid = driver(2L, 12.9900, 77.6100);    // about 2.6 km away
        Driver far = driver(3L, 13.1000, 77.7000);    // about 18 km away
        when(driverRepository.findByAvailableTrue()).thenReturn(List.of(far, mid, near));
        when(driverRepository.claimIfAvailable(1L)).thenReturn(1);

        Optional<Driver> result = matchingService.findAndClaimNearestDriver(PICKUP_LAT, PICKUP_LNG);

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(1L);
        verify(driverRepository, never()).claimIfAvailable(2L);
    }

    @Test
    void nextNearestDriverIsUsedWhenTheNearestWasJustTaken() {
        Driver near = driver(1L, 12.9720, 77.5950);
        Driver mid = driver(2L, 12.9900, 77.6100);
        when(driverRepository.findByAvailableTrue()).thenReturn(List.of(near, mid));
        when(driverRepository.claimIfAvailable(1L)).thenReturn(0);   // someone else claimed it first
        when(driverRepository.claimIfAvailable(2L)).thenReturn(1);

        Optional<Driver> result = matchingService.findAndClaimNearestDriver(PICKUP_LAT, PICKUP_LNG);

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(2L);
    }

    @Test
    void driversOutsideTheRadiusAreIgnored() {
        Driver far = driver(3L, 13.1000, 77.7000);
        when(driverRepository.findByAvailableTrue()).thenReturn(List.of(far));

        Optional<Driver> result = matchingService.findAndClaimNearestDriver(PICKUP_LAT, PICKUP_LNG);

        assertThat(result).isEmpty();
        verify(driverRepository, never()).claimIfAvailable(anyLong());
    }

    @Test
    void driversWithoutALocationAreIgnored() {
        Driver unknown = driver(4L, null, null);
        when(driverRepository.findByAvailableTrue()).thenReturn(List.of(unknown));

        Optional<Driver> result = matchingService.findAndClaimNearestDriver(PICKUP_LAT, PICKUP_LNG);

        assertThat(result).isEmpty();
    }

    @Test
    void noOnlineDriversMeansNoMatch() {
        when(driverRepository.findByAvailableTrue()).thenReturn(List.of());

        assertThat(matchingService.findAndClaimNearestDriver(PICKUP_LAT, PICKUP_LNG)).isEmpty();
    }
}