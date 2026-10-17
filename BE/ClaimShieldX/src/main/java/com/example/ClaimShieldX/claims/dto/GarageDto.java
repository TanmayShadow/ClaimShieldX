package com.example.ClaimShieldX.claims.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GarageDto(
        @NotBlank(message = "Garage id cannot be blank")
        @Size(max = 255, message = "Garage id cannot exceed 255 characters")
        String garageId
) {
}
