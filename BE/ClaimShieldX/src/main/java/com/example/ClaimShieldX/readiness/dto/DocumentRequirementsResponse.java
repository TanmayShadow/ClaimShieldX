package com.example.ClaimShieldX.readiness.dto;

import com.example.ClaimShieldX.claims.model.ClaimStage;
import com.example.ClaimShieldX.claims.model.IncidentType;
import com.example.ClaimShieldX.readiness.model.Readiness;

import java.util.List;
import java.util.UUID;

/**
 * The stage-aware checklist and what it means for readiness. {@code blockers} holds only a missing or failed
 * document that defines the stage (these make the claim NOT_READY); every other gap is a warning.
 */
public record DocumentRequirementsResponse(
        UUID claimId,
        ClaimStage stage,
        IncidentType incidentType,
        Readiness readiness,
        List<ChecklistItem> items,
        List<ReadinessIssue> blockers,
        List<ReadinessIssue> warnings
) {
}
