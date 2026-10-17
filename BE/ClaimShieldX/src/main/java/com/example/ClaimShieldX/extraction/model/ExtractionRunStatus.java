package com.example.ClaimShieldX.extraction.model;

/** The outcome of one extraction attempt. The document's own status (VERIFIED, NEEDS_REVIEW, FAILED) is separate. */
public enum ExtractionRunStatus {
    SUCCEEDED,
    FAILED
}
