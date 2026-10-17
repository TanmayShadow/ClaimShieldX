package com.example.ClaimShieldX.readiness.model;

import com.example.ClaimShieldX.documents.model.DocumentStatus;

/** What has arrived for one checklist entry. Derived from the documents, never stored (data-contract section 3.4). */
public enum EvidenceState {
    NOT_RECEIVED,
    RECEIVED,
    PROCESSING,
    VERIFIED,
    NEEDS_REVIEW,
    FAILED;

    public static EvidenceState of(DocumentStatus status) {
        return switch (status) {
            case RECEIVED -> RECEIVED;
            case PROCESSING -> PROCESSING;
            case VERIFIED -> VERIFIED;
            case NEEDS_REVIEW -> NEEDS_REVIEW;
            case FAILED -> FAILED;
        };
    }

    /** True when the entry cannot be used by a check: nothing arrived, or the only copy failed processing. */
    public boolean isUnusable() {
        return this == NOT_RECEIVED || this == FAILED;
    }
}
