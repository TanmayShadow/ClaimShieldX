package com.example.ClaimShieldX.exceptions;

import java.util.UUID;

/** The claim already has an investigation that has not finished. */
public class InvestigationAlreadyActiveException extends RuntimeException {
    private final UUID activeInvestigationId;

    public InvestigationAlreadyActiveException(UUID activeInvestigationId) {
        super("The claim already has an active investigation");
        this.activeInvestigationId = activeInvestigationId;
    }

    public UUID getActiveInvestigationId() {
        return activeInvestigationId;
    }
}
