package com.nithin.ridebooking.dto;

import jakarta.validation.constraints.NotNull;

public record FareEstimateRequest(
        @NotNull(message = "pickupLatitude is required") Double pickupLatitude,
        @NotNull(message = "pickupLongitude is required") Double pickupLongitude,
        @NotNull(message = "dropLatitude is required") Double dropLatitude,
        @NotNull(message = "dropLongitude is required") Double dropLongitude
) {}