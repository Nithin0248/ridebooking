package com.nithin.ridebooking.dto;

import jakarta.validation.constraints.NotBlank;

public record DriverProfileRequest(
        @NotBlank(message = "vehicleModel is required") String vehicleModel,
        @NotBlank(message = "vehicleNumber is required") String vehicleNumber,
        @NotBlank(message = "licenseNumber is required") String licenseNumber
) {}