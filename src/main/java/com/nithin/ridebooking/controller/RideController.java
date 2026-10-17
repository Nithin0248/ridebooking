package com.nithin.ridebooking.controller;

import com.nithin.ridebooking.dto.RideRequest;
import com.nithin.ridebooking.dto.RideResponse;
import com.nithin.ridebooking.service.RideService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.nithin.ridebooking.dto.FareEstimate;
import com.nithin.ridebooking.dto.FareEstimateRequest;
import com.nithin.ridebooking.service.FareService;
@RestController
@RequestMapping("/api/rides")
@RequiredArgsConstructor
public class RideController {

    private final RideService rideService;
    private final FareService fareService;
    @PostMapping
    public ResponseEntity<RideResponse> bookRide(@Valid @RequestBody RideRequest request,
                                                 Authentication authentication) {
        RideResponse response = rideService.bookRide(authentication.getName(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    @PostMapping("/estimate")
    public FareEstimate estimateFare(@Valid @RequestBody FareEstimateRequest request) {
        return fareService.estimate(
                request.pickupLatitude(), request.pickupLongitude(),
                request.dropLatitude(), request.dropLongitude());
    }

    @GetMapping("/{id}")
    public RideResponse getRide(@PathVariable Long id, Authentication authentication) {
        return rideService.getRide(authentication.getName(), id);
    }

    @PutMapping("/{id}/cancel")
    public RideResponse cancelRide(@PathVariable Long id, Authentication authentication) {
        return rideService.cancelRide(authentication.getName(), id);
    }
}