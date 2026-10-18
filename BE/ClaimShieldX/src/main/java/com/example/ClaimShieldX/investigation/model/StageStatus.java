package com.example.ClaimShieldX.investigation.model;

public enum StageStatus {
    PENDING,
    RUNNING,
    OK,
    /** Finished, but part of the work could not be done (a document unreadable, a retry limit reached). The investigation goes on. */
    DEGRADED,
    FAILED,
    /** Nothing to do, or not available yet. */
    SKIPPED;

    /** A stage that does not have to run again, whether on retry or after a crash. */
    public boolean isSettled() {
        return this == OK || this == DEGRADED || this == SKIPPED;
    }
}
