package com.example.ClaimShieldX.claims.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record PolicyDto(
        @NotBlank(message = "Policy token cannot be blank")
        @Size(max = 255, message = "Policy token cannot exceed 255 characters")
        String policyToken,
        @Size(max = 100, message = "Policy type cannot exceed 100 characters")
        String policyType,
        Instant policyStartDate,
        Instant policyEndDate
) {
}
