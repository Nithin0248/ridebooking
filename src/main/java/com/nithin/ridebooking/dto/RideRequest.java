package com.nithin.ridebooking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RideRequest(
        @NotNull(message = "riderId is required") Long riderId,
        @NotBlank(message = "pickupAddress is required") String pickupAddress,
        @NotNull(message = "pickupLatitude is required") Double pickupLatitude,
        @NotNull(message = "pickupLongitude is required") Double pickupLongitude,
        @NotBlank(message = "dropAddress is required") String dropAddress,
        @NotNull(message = "dropLatitude is required") Double dropLatitude,
        @NotNull(message = "dropLongitude is required") Double dropLongitude
) {}