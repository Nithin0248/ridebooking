package com.nithin.ridebooking.dto;

import jakarta.validation.constraints.NotNull;

public record AvailabilityRequest(
        @NotNull(message = "available is required") Boolean available
) {}