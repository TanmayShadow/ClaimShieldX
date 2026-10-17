package com.example.ClaimShieldX.claims.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VehicleDto(
        @NotBlank(message = "Vehicle token cannot be blank")
        @Size(max = 255, message = "Vehicle token cannot exceed 255 characters")
        String vehicleToken,
        @Size(max = 100, message = "Vehicle make cannot exceed 100 characters")
        String make,
        @Size(max = 100, message = "Vehicle model cannot exceed 100 characters")
        String model
) {
}
