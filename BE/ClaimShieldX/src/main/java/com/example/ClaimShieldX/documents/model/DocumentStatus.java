package com.example.ClaimShieldX.documents.model;

// NOT_RECEIVED is not here on purpose: it describes the absence of a document (data-contract section 2).
public enum DocumentStatus {
    RECEIVED,
    PROCESSING,
    VERIFIED,
    NEEDS_REVIEW,
    FAILED
}
