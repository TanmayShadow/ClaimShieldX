package com.example.ClaimShieldX.readiness.dto;

import com.example.ClaimShieldX.documents.model.DocumentType;
import com.example.ClaimShieldX.readiness.model.InvestigationCheck;
import com.example.ClaimShieldX.readiness.model.RequirementLevel;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * A gap in the evidence. The wording is always about documents and checks, never about the claim being suspect:
 * a missing document only means a check could not run.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReadinessIssue(
        Code code,
        DocumentType documentType,
        RequirementLevel requirement,
        String message,
        List<InvestigationCheck> blocks
) {
    public enum Code {
        DOCUMENT_MISSING,
        DOCUMENT_FAILED,
        DOCUMENT_NEEDS_REVIEW,
        DOCUMENT_PROCESSING,
        NO_CHECKLIST_FOR_STAGE
    }
}
