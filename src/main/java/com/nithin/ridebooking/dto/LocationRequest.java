package com.nithin.ridebooking.dto;

import jakarta.validation.constraints.NotNull;

public record LocationRequest(
        @NotNull(message = "latitude is required") Double latitude,
        @NotNull(message = "longitude is required") Double longitude
) {}