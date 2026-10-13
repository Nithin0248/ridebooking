package com.nithin.ridebooking.dto;

import com.nithin.ridebooking.entity.RideStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RideResponse(
        Long id,
        Long riderId,
        String riderName,
        Long driverId,
        String pickupAddress,
        String dropAddress,
        RideStatus status,
        Double distanceKm,
        BigDecimal fare,
        LocalDateTime requestedAt,
        LocalDateTime completedAt
) {}