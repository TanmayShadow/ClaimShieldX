package com.example.ClaimShieldX.history.model;

/** data-contract section 3.5. COMPLETED_WITH_ERRORS keeps the accepted rows and lists the rejected ones. */
public enum ImportStatus {
    RECEIVED,
    PROCESSING,
    COMPLETED,
    COMPLETED_WITH_ERRORS,
    FAILED
}
