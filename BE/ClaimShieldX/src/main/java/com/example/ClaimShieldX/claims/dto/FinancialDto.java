package com.example.ClaimShieldX.claims.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

public record FinancialDto(
        @PositiveOrZero(message = "Initial estimate amount cannot be negative")
        Long initialEstimateAmount,
        @PositiveOrZero(message = "Approved estimate amount cannot be negative")
        Long approvedEstimateAmount,
        @NotBlank(message = "Currency cannot be blank")
        @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter uppercase ISO 4217 code")
        String currency,
        @PositiveOrZero(message = "Deductible cannot be negative")
        Long deductible
) {
}
