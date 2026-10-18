package com.example.ClaimShieldX.rules.dto;

import java.time.Instant;
import java.util.UUID;

public record RuleRunSummary(UUID id, Instant createdAt, UUID requestedBy, String ruleSetVersion, RuleRunCounts counts) {
}
