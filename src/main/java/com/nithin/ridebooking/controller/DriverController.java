package com.nithin.ridebooking.controller;

import com.nithin.ridebooking.dto.*;
import com.nithin.ridebooking.service.DriverService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/drivers")
@RequiredArgsConstructor
public class DriverController {

    private final DriverService driverService;

    @PostMapping("/profile")
    public ResponseEntity<DriverResponse> createProfile(@Valid @RequestBody DriverProfileRequest request,
                                                        Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(driverService.createProfile(authentication.getName(), request));
    }

    @GetMapping("/me")
    public DriverResponse me(Authentication authentication) {
        return driverService.getMyProfile(authentication.getName());
    }

    @PutMapping("/me/availability")
    public DriverResponse setAvailability(@Valid @RequestBody AvailabilityRequest request,
                                          Authentication authentication) {
        return driverService.setAvailability(authentication.getName(), request.available());
    }

    @PutMapping("/me/location")
    public DriverResponse updateLocation(@Valid @RequestBody LocationRequest request,
                                         Authentication authentication) {
        return driverService.updateLocation(authentication.getName(),
                request.latitude(), request.longitude());
    }
}