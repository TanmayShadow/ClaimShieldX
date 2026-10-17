package com.example.ClaimShieldX.claims.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record IncidentDto(
        @NotNull(message = "Incident date and time is required")
        Instant incidentAt,
        Instant reportedAt,
        @Size(max = 100, message = "Location precision cannot exceed 100 characters")
        String permittedLocationPrecision
) {
}
