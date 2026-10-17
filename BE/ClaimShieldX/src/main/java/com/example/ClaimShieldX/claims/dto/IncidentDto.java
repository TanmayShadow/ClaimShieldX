package com.example.ClaimShieldX.claims.dto;

import com.example.ClaimShieldX.claims.model.IncidentType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record IncidentDto(
        @NotNull(message = "Incident date and time is required")
        Instant incidentAt,
        Instant reportedAt,
        @NotNull(message = "Incident type is required")
        IncidentType incidentType,
        @Size(max = 100, message = "Location precision cannot exceed 100 characters")
        String permittedLocationPrecision
) {
}
