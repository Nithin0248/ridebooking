package com.nithin.ridebooking.service;

import com.nithin.ridebooking.dto.LocationUpdate;
import com.nithin.ridebooking.entity.Driver;
import com.nithin.ridebooking.entity.Ride;
import com.nithin.ridebooking.entity.RideStatus;
import com.nithin.ridebooking.exception.BadRequestException;
import com.nithin.ridebooking.exception.ResourceNotFoundException;
import com.nithin.ridebooking.repository.RideRepository;
import com.nithin.ridebooking.util.GeoUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class TrackingService {

    private final RideRepository rideRepository;

    @Transactional
    public LocationUpdate processDriverLocation(String email, Long rideId,
                                                Double latitude, Double longitude) {
        if (latitude == null || longitude == null) {
            throw new BadRequestException("latitude and longitude are required");
        }
        if (!GeoUtils.isValidCoordinate(latitude, longitude)) {
            throw new BadRequestException(
                    "Invalid coordinates: latitude must be -90 to 90, longitude -180 to 180");
        }

        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new ResourceNotFoundException("Ride not found with id " + rideId));

        Driver driver = ride.getDriver();
        boolean assigned = driver != null && driver.getUser().getEmail().equals(email);
        if (!assigned) {
            throw new AccessDeniedException(
                    "Only the assigned driver can share location for this ride");
        }

        if (ride.getStatus() != RideStatus.ACCEPTED && ride.getStatus() != RideStatus.IN_PROGRESS) {
            throw new BadRequestException("Ride is not active");
        }

        // Managed entity: this change is saved automatically when the transaction commits
        driver.setCurrentLatitude(latitude);
        driver.setCurrentLongitude(longitude);

        return new LocationUpdate(rideId, latitude, longitude, Instant.now().toString());
    }
}