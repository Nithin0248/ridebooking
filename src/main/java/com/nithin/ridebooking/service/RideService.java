package com.nithin.ridebooking.service;

import com.nithin.ridebooking.dto.FareEstimate;
import com.nithin.ridebooking.dto.RideRequest;
import com.nithin.ridebooking.dto.RideResponse;
import com.nithin.ridebooking.dto.RideStatusMessage;
import com.nithin.ridebooking.entity.*;
import com.nithin.ridebooking.exception.BadRequestException;
import com.nithin.ridebooking.exception.ResourceNotFoundException;
import com.nithin.ridebooking.repository.RideRepository;
import com.nithin.ridebooking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.nithin.ridebooking.dto.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RideService {

    private static final List<RideStatus> ACTIVE_STATUSES =
            List.of(RideStatus.REQUESTED, RideStatus.ACCEPTED, RideStatus.IN_PROGRESS);

    private final RideRepository rideRepository;
    private final UserRepository userRepository;
    private final FareService fareService;
    private final MatchingService matchingService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public RideResponse bookRide(String email, RideRequest request) {
        User rider = findUser(email);

        if (rider.getRole() != Role.RIDER) {
            throw new BadRequestException("Only riders can book rides");
        }
        if (rideRepository.existsByRiderIdAndStatusIn(rider.getId(), ACTIVE_STATUSES)) {
            throw new BadRequestException("You already have an active ride");
        }

        FareEstimate fare = fareService.estimate(
                request.pickupLatitude(), request.pickupLongitude(),
                request.dropLatitude(), request.dropLongitude());

        Optional<Driver> match = matchingService.findAndClaimNearestDriver(
                request.pickupLatitude(), request.pickupLongitude());

        Ride ride = Ride.builder()
                .rider(rider)
                .driver(match.orElse(null))
                .pickupAddress(request.pickupAddress())
                .pickupLatitude(request.pickupLatitude())
                .pickupLongitude(request.pickupLongitude())
                .dropAddress(request.dropAddress())
                .dropLatitude(request.dropLatitude())
                .dropLongitude(request.dropLongitude())
                .distanceKm(fare.distanceKm())
                .fare(fare.total())
                .status(match.isPresent() ? RideStatus.ACCEPTED : RideStatus.REQUESTED)
                .build();

        Ride saved = rideRepository.save(ride);
        publishStatus(saved);
        return toResponse(saved);
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
    @Transactional(readOnly = true)
    public PageResponse<RideResponse> history(String email, RideStatus status, int page, int size) {
        User caller = findUser(email);

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 50);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "requestedAt"));

        Page<Ride> result;
        if (caller.getRole() == Role.DRIVER) {
            result = (status == null)
                    ? rideRepository.findByDriverUserId(caller.getId(), pageable)
                    : rideRepository.findByDriverUserIdAndStatus(caller.getId(), status, pageable);
        } else {
            result = (status == null)
                    ? rideRepository.findByRiderId(caller.getId(), pageable)
                    : rideRepository.findByRiderIdAndStatus(caller.getId(), status, pageable);
        }

        return PageResponse.from(result.map(this::toResponse));
    }
    @Transactional(readOnly = true)
    public boolean isParticipant(String email, Long rideId) {
        return userRepository.findByEmail(email)
                .flatMap(user -> rideRepository.findById(rideId)
                        .map(ride -> canView(user, ride)))
                .orElse(false);
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

        if (ride.getDriver() != null) {
            ride.getDriver().setAvailable(true);
        }

        ride.setStatus(RideStatus.CANCELLED);
        Ride saved = rideRepository.save(ride);
        publishStatus(saved);
        return toResponse(saved);
    }

    @Transactional
    public RideResponse startRide(String email, Long id) {
        User caller = findUser(email);
        Ride ride = findRide(id);
        assertAssignedDriver(caller, ride);

        if (ride.getStatus() != RideStatus.ACCEPTED) {
            throw new BadRequestException(
                    "Ride cannot be started when status is " + ride.getStatus());
        }

        ride.setStatus(RideStatus.IN_PROGRESS);
        Ride saved = rideRepository.save(ride);
        publishStatus(saved);
        return toResponse(saved);
    }

    @Transactional
    public RideResponse completeRide(String email, Long id) {
        User caller = findUser(email);
        Ride ride = findRide(id);
        assertAssignedDriver(caller, ride);

        if (ride.getStatus() != RideStatus.IN_PROGRESS) {
            throw new BadRequestException(
                    "Ride cannot be completed when status is " + ride.getStatus());
        }

        ride.setStatus(RideStatus.COMPLETED);
        ride.setCompletedAt(LocalDateTime.now());

        Driver driver = ride.getDriver();
        driver.setAvailable(true);
        driver.setCurrentLatitude(ride.getDropLatitude());
        driver.setCurrentLongitude(ride.getDropLongitude());

        Ride saved = rideRepository.save(ride);
        publishStatus(saved);
        return toResponse(saved);
    }

    private void publishStatus(Ride ride) {
        Driver d = ride.getDriver();
        eventPublisher.publishEvent(new RideStatusMessage(
                ride.getId(),
                ride.getStatus(),
                d != null ? d.getId() : null,
                d != null ? d.getUser().getName() : null,
                d != null ? d.getVehicleModel() : null,
                d != null ? d.getVehicleNumber() : null,
                Instant.now().toString()));
    }

    private void assertAssignedDriver(User caller, Ride ride) {
        boolean assigned = ride.getDriver() != null
                && ride.getDriver().getUser().getId().equals(caller.getId());
        if (!assigned) {
            throw new AccessDeniedException("Only the assigned driver can do this");
        }
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
        Driver d = ride.getDriver();
        return new RideResponse(
                ride.getId(),
                ride.getRider().getId(),
                ride.getRider().getName(),
                d != null ? d.getId() : null,
                d != null ? d.getUser().getName() : null,
                d != null ? d.getVehicleModel() : null,
                d != null ? d.getVehicleNumber() : null,
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