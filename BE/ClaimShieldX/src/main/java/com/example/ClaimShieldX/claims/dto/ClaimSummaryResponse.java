package com.example.ClaimShieldX.claims.dto;

import com.example.ClaimShieldX.claims.model.ClaimStage;
import com.example.ClaimShieldX.readiness.dto.ReadinessSummary;

import java.time.Instant;
import java.util.UUID;

/** One row of the claim list: enough for a dashboard, without the claim's personal or financial detail. */
public record ClaimSummaryResponse(
        UUID id,
        String internalId,
        ClaimStage currentStage,
        Instant createdAt,
        ReadinessSummary readiness
) {
}
