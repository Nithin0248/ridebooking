package com.nithin.ridebooking.service;

import com.nithin.ridebooking.dto.DriverProfileRequest;
import com.nithin.ridebooking.dto.DriverResponse;
import com.nithin.ridebooking.entity.*;
import com.nithin.ridebooking.exception.BadRequestException;
import com.nithin.ridebooking.exception.ResourceNotFoundException;
import com.nithin.ridebooking.repository.DriverRepository;
import com.nithin.ridebooking.repository.RideRepository;
import com.nithin.ridebooking.repository.UserRepository;
import com.nithin.ridebooking.util.GeoUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DriverService {

    private final DriverRepository driverRepository;
    private final UserRepository userRepository;
    private final RideRepository rideRepository;

    @Transactional
    public DriverResponse createProfile(String email, DriverProfileRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));

        if (user.getRole() != Role.DRIVER) {
            throw new BadRequestException("Only users registered as DRIVER can create a driver profile");
        }
        if (driverRepository.findByUserId(user.getId()).isPresent()) {
            throw new BadRequestException("Driver profile already exists");
        }

        String vehicleNumber = request.vehicleNumber().trim().toUpperCase();
        String licenseNumber = request.licenseNumber().trim().toUpperCase();

        if (driverRepository.existsByVehicleNumber(vehicleNumber)) {
            throw new BadRequestException("Vehicle number is already registered");
        }
        if (driverRepository.existsByLicenseNumber(licenseNumber)) {
            throw new BadRequestException("License number is already registered");
        }

        Driver driver = driverRepository.save(Driver.builder()
                .user(user)
                .vehicleModel(request.vehicleModel().trim())
                .vehicleNumber(vehicleNumber)
                .licenseNumber(licenseNumber)
                .available(false)
                .build());

        return toResponse(driver);
    }

    @Transactional(readOnly = true)
    public DriverResponse getMyProfile(String email) {
        return toResponse(findDriver(email));
    }

    @Transactional
    public DriverResponse setAvailability(String email, boolean available) {
        Driver driver = findDriver(email);

        boolean onRide = rideRepository.existsByDriverIdAndStatusIn(
                driver.getId(), List.of(RideStatus.ACCEPTED, RideStatus.IN_PROGRESS));
        if (onRide) {
            throw new BadRequestException("Finish your current ride before changing availability");
        }

        if (available && (driver.getCurrentLatitude() == null || driver.getCurrentLongitude() == null)) {
            throw new BadRequestException("Update your location before going online");
        }

        driver.setAvailable(available);
        return toResponse(driverRepository.save(driver));
    }

    @Transactional
    public DriverResponse updateLocation(String email, double latitude, double longitude) {
        if (!GeoUtils.isValidCoordinate(latitude, longitude)) {
            throw new BadRequestException(
                    "Invalid coordinates: latitude must be -90 to 90, longitude -180 to 180");
        }

        Driver driver = findDriver(email);
        driver.setCurrentLatitude(latitude);
        driver.setCurrentLongitude(longitude);
        return toResponse(driverRepository.save(driver));
    }

    private Driver findDriver(String email) {
        return driverRepository.findByUserEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Driver profile not found. Create one first"));
    }

    private DriverResponse toResponse(Driver d) {
        return new DriverResponse(d.getId(), d.getUser().getId(), d.getUser().getName(),
                d.getVehicleModel(), d.getVehicleNumber(), d.getLicenseNumber(),
                d.isAvailable(), d.getCurrentLatitude(), d.getCurrentLongitude());
    }
}