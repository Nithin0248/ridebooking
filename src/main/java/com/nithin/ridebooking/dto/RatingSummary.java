package com.nithin.ridebooking.dto;

public record RatingSummary(
        Long userId,
        Double averageStars,   // null when there are no ratings yet
        long ratingCount,
        boolean lowRated
) {}