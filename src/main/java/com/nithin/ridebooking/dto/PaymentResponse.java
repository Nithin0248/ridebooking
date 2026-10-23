package com.nithin.ridebooking.dto;

import com.nithin.ridebooking.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponse(
        Long paymentId,
        Long rideId,
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        String clientSecret,      // only filled in when the payment is created
        LocalDateTime paidAt
) {}