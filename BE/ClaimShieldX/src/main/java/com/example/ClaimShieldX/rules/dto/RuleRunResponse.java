package com.example.ClaimShieldX.rules.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A stored evaluation of every rule for one claim. It never changes: new evidence produces a new run. {@code reusedExistingRun}
 * is only present on the answer to a POST, and is true when the inputs were identical to an earlier run, so that run is returned
 * instead of a new one (the rules are deterministic, so a new run could not differ).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RuleRunResponse(UUID id, UUID claimId, String ruleSetVersion, Instant createdAt, UUID requestedBy, RuleRunCounts counts,
                              Map<String, Object> configuration, String configurationHash, String inputFingerprint, Map<String, Object> inputs,
                              Boolean reusedExistingRun, List<RuleResultResponse> results) {
}
