package com.example.ClaimShieldX.readiness.model;

/** What the checklist asks for. Separate from {@link EvidenceState}, which says what has actually arrived. */
public enum RequirementLevel {
    REQUIRED,
    OPTIONAL,
    NOT_APPLICABLE
}
