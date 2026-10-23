package com.nithin.ridebooking.service;

import com.nithin.ridebooking.dto.*;
import com.nithin.ridebooking.entity.*;
import com.nithin.ridebooking.exception.BadRequestException;
import com.nithin.ridebooking.exception.ResourceNotFoundException;
import com.nithin.ridebooking.repository.DriverRepository;
import com.nithin.ridebooking.repository.RatingRepository;
import com.nithin.ridebooking.repository.RideRepository;
import com.nithin.ridebooking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RatingService {

    // A user is flagged only after enough ratings, so one bad ride cannot flag anyone
    private static final int LOW_RATING_MIN_COUNT = 5;
    private static final double LOW_RATING_THRESHOLD = 3.5;

    private final RatingRepository ratingRepository;
    private final RideRepository rideRepository;
    private final UserRepository userRepository;
    private final DriverRepository driverRepository;

    @Transactional
    public RatingResponse rateRide(String email, Long rideId, RatingRequest request) {
        User caller = findUser(email);
        Ride ride = findRide(rideId);

        if (ride.getStatus() != RideStatus.COMPLETED) {
            throw new BadRequestException("Only completed rides can be rated");
        }
        if (ride.getDriver() == null) {
            throw new BadRequestException("This ride has no driver to rate");
        }

        User rider = ride.getRider();
        User driverUser = ride.getDriver().getUser();

        // The server decides who is being rated. The client cannot choose.
        User ratee;
        if (caller.getId().equals(rider.getId())) {
            ratee = driverUser;
        } else if (caller.getId().equals(driverUser.getId())) {
            ratee = rider;
        } else {
            throw new AccessDeniedException("Only the rider or the driver of this ride can rate it");
        }

        if (ratingRepository.existsByRideIdAndRaterId(rideId, caller.getId())) {
            throw new BadRequestException("You already rated this ride");
        }

        Rating rating = Rating.builder()
                .ride(ride)
                .rater(caller)
                .ratee(ratee)
                .stars(request.stars())
                .comment(cleanComment(request.comment()))
                .build();

        try {
            return toResponse(ratingRepository.save(rating));
        } catch (DataIntegrityViolationException e) {
            // Two requests arrived at the same moment: the database unique constraint stopped the second
            throw new BadRequestException("You already rated this ride");
        }
    }

    @Transactional(readOnly = true)
    public List<RatingResponse> getRideRatings(String email, Long rideId) {
        User caller = findUser(email);
        Ride ride = findRide(rideId);

        boolean isRider = ride.getRider().getId().equals(caller.getId());
        boolean isDriver = ride.getDriver() != null
                && ride.getDriver().getUser().getId().equals(caller.getId());
        if (!isRider && !isDriver && caller.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("You are not allowed to view these ratings");
        }

        return ratingRepository.findByRideId(rideId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public RatingSummary getMySummary(String email) {
        return summaryFor(findUser(email).getId());
    }

    @Transactional(readOnly = true)
    public RatingSummary getDriverSummary(Long driverId) {
        return summaryFor(findDriver(driverId).getUser().getId());
    }

    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> getDriverReviews(Long driverId, int page, int size) {
        Long userId = findDriver(driverId).getUser().getId();

        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Rating> result = ratingRepository.findByRateeId(userId, pageable);

        return PageResponse.from(result.map(r -> new ReviewResponse(
                r.getId(), r.getStars(), r.getComment(),
                firstName(r.getRater().getName()), r.getCreatedAt())));
    }

    private RatingSummary summaryFor(Long userId) {
        long count = ratingRepository.countByRateeId(userId);
        Double average = ratingRepository.averageStars(userId);

        Double rounded = null;
        if (average != null) {
            rounded = Math.round(average * 100.0) / 100.0;
        }
        boolean lowRated = rounded != null
                && count >= LOW_RATING_MIN_COUNT
                && rounded < LOW_RATING_THRESHOLD;

        return new RatingSummary(userId, rounded, count, lowRated);
    }

    private String cleanComment(String comment) {
        if (comment == null || comment.isBlank()) {
            return null;
        }
        return comment.trim();
    }

    private String firstName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "User";
        }
        return fullName.trim().split("\\s+")[0];
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    }

    private Ride findRide(Long id) {
        return rideRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ride not found with id " + id));
    }

    private Driver findDriver(Long driverId) {
        return driverRepository.findById(driverId)
                .orElseThrow(() -> new ResourceNotFoundException("Driver not found with id " + driverId));
    }

    private RatingResponse toResponse(Rating r) {
        return new RatingResponse(r.getId(), r.getRide().getId(), r.getRater().getId(),
                r.getRatee().getId(), r.getStars(), r.getComment(), r.getCreatedAt());
    }
}