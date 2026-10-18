package com.nithin.ridebooking.dto;

import com.nithin.ridebooking.entity.RideStatus;

public record RideStatusMessage(
        Long rideId,
        RideStatus status,
        Long driverId,
        String driverName,
        String vehicleModel,
        String vehicleNumber,
        String updatedAt
) {}