package com.example.ClaimShieldX.claims.dto;

import com.example.ClaimShieldX.claims.model.ClaimStage;

import java.time.Instant;
import java.util.UUID;

public record ClaimResponse(
        UUID id,
        String internalId,
        long claimVersion,
        ClaimStage currentStage,
        PolicyDto policy,
        ClaimantDto claimant,
        GarageDto garage,
        VehicleDto vehicle,
        IncidentDto incident,
        FinancialDto financial,
        Instant createdAt,
        Instant updatedAt
) {
}
