package com.nithin.ridebooking.dto;

import java.time.LocalDateTime;

public record ReviewResponse(
        Long id,
        int stars,
        String comment,
        String raterFirstName,
        LocalDateTime createdAt
) {}