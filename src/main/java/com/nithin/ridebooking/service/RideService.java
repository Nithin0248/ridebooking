package com.nithin.ridebooking.service;

import com.nithin.ridebooking.dto.RideRequest;
import com.nithin.ridebooking.dto.RideResponse;
import com.nithin.ridebooking.entity.*;
import com.nithin.ridebooking.exception.BadRequestException;
import com.nithin.ridebooking.exception.ResourceNotFoundException;
import com.nithin.ridebooking.repository.RideRepository;
import com.nithin.ridebooking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RideService {

    private final RideRepository rideRepository;
    private final UserRepository userRepository;

    @Transactional
    public RideResponse bookRide(String email, RideRequest request) {
        User rider = findUser(email);

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
    public RideResponse getRide(String email, Long id) {
        User caller = findUser(email);
        Ride ride = findRide(id);

        if (!canView(caller, ride)) {
            throw new AccessDeniedException("You are not allowed to view this ride");
        }
        return toResponse(ride);
    }

    @Transactional
    public RideResponse cancelRide(String email, Long id) {
        User caller = findUser(email);
        Ride ride = findRide(id);

        boolean isOwner = ride.getRider().getId().equals(caller.getId());
        if (!isOwner && caller.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("You are not allowed to cancel this ride");
        }

        if (ride.getStatus() != RideStatus.REQUESTED
                && ride.getStatus() != RideStatus.ACCEPTED) {
            throw new BadRequestException(
                    "Ride cannot be cancelled when status is " + ride.getStatus());
        }

        ride.setStatus(RideStatus.CANCELLED);
        return toResponse(rideRepository.save(ride));
    }

    private boolean canView(User caller, Ride ride) {
        if (caller.getRole() == Role.ADMIN) return true;
        if (ride.getRider().getId().equals(caller.getId())) return true;
        return ride.getDriver() != null
                && ride.getDriver().getUser().getId().equals(caller.getId());
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
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