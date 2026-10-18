package com.example.ClaimShieldX.investigation.work;

import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * What a stage handler is given. {@code shouldStop} is true when the investigation was cancelled or this worker no longer holds
 * its lease; a handler that works through several items checks it between items and returns {@link StageOutcome.Interrupted}.
 */
public record StageContext(UUID investigationId, UUID organizationId, UUID claimId, UUID requestedBy, int attempt, int maxAttempts, BooleanSupplier shouldStop) {
}
