package com.nithin.ridebooking.dto;

import java.time.LocalDateTime;

public record RatingResponse(
        Long id,
        Long rideId,
        Long raterId,
        Long rateeId,
        int stars,
        String comment,
        LocalDateTime createdAt
) {}