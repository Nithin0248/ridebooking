package com.nithin.ridebooking.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ReceiptResponse(
        String receiptNumber,
        Long rideId,
        LocalDateTime requestedAt,
        LocalDateTime completedAt,
        String riderName,
        String driverName,
        String vehicleModel,
        String vehicleNumber,
        String pickupAddress,
        String dropAddress,
        Double distanceKm,
        BigDecimal fare,
        String currency,
        String paymentStatus      // SUCCEEDED, PENDING, FAILED, or NOT_PAID
) {}