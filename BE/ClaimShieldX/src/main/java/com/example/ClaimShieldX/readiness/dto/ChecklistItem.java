package com.example.ClaimShieldX.readiness.dto;

import com.example.ClaimShieldX.documents.model.DocumentType;
import com.example.ClaimShieldX.readiness.model.EvidenceState;
import com.example.ClaimShieldX.readiness.model.InvestigationCheck;
import com.example.ClaimShieldX.readiness.model.RequirementLevel;

import java.util.List;
import java.util.UUID;

/**
 * One line of the checklist. {@code requirement} and {@code evidenceState} are separate on purpose: what is asked
 * for and what has arrived are never mixed. {@code mandatory} marks the one document the user cannot proceed
 * without (the document the stage's review is about); every other entry, however it is marked, lets the user
 * proceed when it is missing. {@code documentIds} lists every current (non-superseded) document of the type,
 * newest first; {@code evidenceState} comes from the newest one. {@code blocks} names the checks that cannot run
 * because this entry is missing or failed.
 */
public record ChecklistItem(
        DocumentType documentType,
        RequirementLevel requirement,
        boolean mandatory,
        EvidenceState evidenceState,
        List<UUID> documentIds,
        List<InvestigationCheck> blocks
) {
}
