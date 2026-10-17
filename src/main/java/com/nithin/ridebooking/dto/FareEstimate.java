package com.nithin.ridebooking.dto;

import java.math.BigDecimal;

public record FareEstimate(
        double distanceKm,
        int durationMinutes,
        BigDecimal baseFare,
        BigDecimal distanceCharge,
        BigDecimal timeCharge,
        BigDecimal surgeMultiplier,
        BigDecimal total
) {}