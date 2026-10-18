package com.nithin.ridebooking.service;

import com.nithin.ridebooking.entity.Driver;
import com.nithin.ridebooking.repository.DriverRepository;
import com.nithin.ridebooking.util.GeoUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class MatchingService {

    private final DriverRepository driverRepository;
    private final double maxRadiusKm;

    public MatchingService(DriverRepository driverRepository,
                           @Value("${matching.max-radius-km}") double maxRadiusKm) {
        this.driverRepository = driverRepository;
        this.maxRadiusKm = maxRadiusKm;
    }

    @Transactional
    public Optional<Driver> findAndClaimNearestDriver(double pickupLat, double pickupLng) {

        List<Driver> candidates = driverRepository.findByAvailableTrue().stream()
                .filter(d -> d.getCurrentLatitude() != null && d.getCurrentLongitude() != null)
                .filter(d -> distanceKm(d, pickupLat, pickupLng) <= maxRadiusKm)
                .sorted(Comparator.comparingDouble(d -> distanceKm(d, pickupLat, pickupLng)))
                .toList();

        for (Driver driver : candidates) {
            // Only one booking can win this driver, even if several run at the same moment
            if (driverRepository.claimIfAvailable(driver.getId()) == 1) {
                return Optional.of(driver);
            }
        }
        return Optional.empty();
    }

    private double distanceKm(Driver driver, double lat, double lng) {
        return GeoUtils.distanceKm(driver.getCurrentLatitude(), driver.getCurrentLongitude(), lat, lng);
    }
}