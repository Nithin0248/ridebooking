package com.nithin.ridebooking.controller;

import com.nithin.ridebooking.dto.RideRequest;
import com.nithin.ridebooking.dto.RideResponse;
import com.nithin.ridebooking.service.RideService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rides")
@RequiredArgsConstructor
public class RideController {

    private final RideService rideService ;

    @PostMapping
    public ResponseEntity<RideResponse> bookRide(@Valid @RequestBody RideRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(rideService.bookRide(request));
    }

    @GetMapping("/{id}")
    public RideResponse getRide(@PathVariable Long id) {
        return rideService.getRide(id);
    }

    @PutMapping("/{id}/cancel")
    public RideResponse cancelRide(@PathVariable Long id) {
        return rideService.cancelRide(id);
    }
}