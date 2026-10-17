package com.example.ClaimShieldX.claims.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

// More than 2 decimals is rejected rather than rounded: rounding would alter the insurer's data.
public record FinancialDto(
        @PositiveOrZero(message = "Initial estimate amount cannot be negative")
        @Digits(integer = 13, fraction = 2, message = "Initial estimate amount allows at most 13 whole digits and 2 decimals")
        BigDecimal initialEstimateAmount,
        @PositiveOrZero(message = "Approved estimate amount cannot be negative")
        @Digits(integer = 13, fraction = 2, message = "Approved estimate amount allows at most 13 whole digits and 2 decimals")
        BigDecimal approvedEstimateAmount,
        @NotBlank(message = "Currency cannot be blank")
        @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter uppercase ISO 4217 code")
        String currency,
        @PositiveOrZero(message = "Deductible cannot be negative")
        @Digits(integer = 13, fraction = 2, message = "Deductible allows at most 13 whole digits and 2 decimals")
        BigDecimal deductible
) {
    // 45000.500 carries no extra precision, so it is accepted; 45000.505 still fails the @Digits check.
    public FinancialDto {
        initialEstimateAmount = withoutTrailingZeros(initialEstimateAmount);
        approvedEstimateAmount = withoutTrailingZeros(approvedEstimateAmount);
        deductible = withoutTrailingZeros(deductible);
    }

    private static BigDecimal withoutTrailingZeros(BigDecimal amount) {
        if (amount == null || amount.scale() <= 2) {
            return amount;
        }
        BigDecimal stripped = amount.stripTrailingZeros();
        return stripped.scale() <= 2 ? stripped : amount;
    }
}
