package com.nithin.ridebooking.dto;

public record LocationUpdate(
        Long rideId,
        double latitude,
        double longitude,
        String timestamp
) {}