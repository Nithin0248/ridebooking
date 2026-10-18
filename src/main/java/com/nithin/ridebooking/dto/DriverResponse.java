package com.nithin.ridebooking.dto;

public record DriverResponse(
        Long id,
        Long userId,
        String name,
        String vehicleModel,
        String vehicleNumber,
        String licenseNumber,
        boolean available,
        Double currentLatitude,
        Double currentLongitude
) {}