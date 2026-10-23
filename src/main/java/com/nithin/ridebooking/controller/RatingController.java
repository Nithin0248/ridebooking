package com.nithin.ridebooking.controller;

import com.nithin.ridebooking.dto.*;
import com.nithin.ridebooking.service.RatingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class RatingController {

    private final RatingService ratingService;

    // Rate the other person on a completed ride
    @PostMapping("/api/rides/{rideId}/rating")
    public ResponseEntity<RatingResponse> rate(@PathVariable Long rideId,
                                               @Valid @RequestBody RatingRequest request,
                                               Authentication authentication) {
        RatingResponse response = ratingService.rateRide(authentication.getName(), rideId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // Ratings given on one ride (rider, driver or admin only)
    @GetMapping("/api/rides/{rideId}/ratings")
    public List<RatingResponse> rideRatings(@PathVariable Long rideId, Authentication authentication) {
        return ratingService.getRideRatings(authentication.getName(), rideId);
    }

    // My own average rating (works for riders and drivers)
    @GetMapping("/api/ratings/me")
    public RatingSummary mySummary(Authentication authentication) {
        return ratingService.getMySummary(authentication.getName());
    }

    // A driver's average rating (the driverId is the one shown in a ride response)
    @GetMapping("/api/drivers/{driverId}/rating")
    public RatingSummary driverSummary(@PathVariable Long driverId) {
        return ratingService.getDriverSummary(driverId);
    }

    // A driver's reviews, newest first
    @GetMapping("/api/drivers/{driverId}/reviews")
    public PageResponse<ReviewResponse> driverReviews(@PathVariable Long driverId,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "10") int size) {
        return ratingService.getDriverReviews(driverId, page, size);
    }
}