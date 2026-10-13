package com.nithin.ridebooking.service;

import com.nithin.ridebooking.dto.RideRequest;
import com.nithin.ridebooking.dto.RideResponse;
import com.nithin.ridebooking.entity.*;
import com.nithin.ridebooking.exception.BadRequestException;
import com.nithin.ridebooking.exception.ResourceNotFoundException;
import com.nithin.ridebooking.repository.RideRepository;
import com.nithin.ridebooking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RideService {

    private final RideRepository rideRepository;
    private final UserRepository userRepository;

    @Transactional
    public RideResponse bookRide(RideRequest request) {
        User rider = userRepository.findById(request.riderId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with id " + request.riderId()));

        if (rider.getRole() != Role.RIDER) {
            throw new BadRequestException("Only riders can book rides");
        }

        Ride ride = Ride.builder()
                .rider(rider)
                .pickupAddress(request.pickupAddress())
                .pickupLatitude(request.pickupLatitude())
                .pickupLongitude(request.pickupLongitude())
                .dropAddress(request.dropAddress())
                .dropLatitude(request.dropLatitude())
                .dropLongitude(request.dropLongitude())
                .status(RideStatus.REQUESTED)
                .build();

        return toResponse(rideRepository.save(ride));
    }

    @Transactional(readOnly = true)
    public RideResponse getRide(Long id) {
        return toResponse(findRide(id));
    }

    @Transactional
    public RideResponse cancelRide(Long id) {
        Ride ride = findRide(id);

        if (ride.getStatus() != RideStatus.REQUESTED
                && ride.getStatus() != RideStatus.ACCEPTED) {
            throw new BadRequestException(
                    "Ride cannot be cancelled when status is " + ride.getStatus());
        }

        ride.setStatus(RideStatus.CANCELLED);
        return toResponse(rideRepository.save(ride));
    }

    private Ride findRide(Long id) {
        return rideRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ride not found with id " + id));
    }

    private RideResponse toResponse(Ride ride) {
        return new RideResponse(
                ride.getId(),
                ride.getRider().getId(),
                ride.getRider().getName(),
                ride.getDriver() != null ? ride.getDriver().getId() : null,
                ride.getPickupAddress(),
                ride.getDropAddress(),
                ride.getStatus(),
                ride.getDistanceKm(),
                ride.getFare(),
                ride.getRequestedAt(),
                ride.getCompletedAt()
        );
    }
}