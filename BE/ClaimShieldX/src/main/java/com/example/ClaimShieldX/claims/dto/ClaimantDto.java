package com.example.ClaimShieldX.claims.dto;

import jakarta.validation.constraints.Size;

public record ClaimantDto(
        @Size(max = 255, message = "Claimant token cannot exceed 255 characters")
        String claimantToken
) {
}
