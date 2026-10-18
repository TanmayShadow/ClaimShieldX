package com.example.ClaimShieldX.rules.dto;

import com.example.ClaimShieldX.rules.model.EvidenceRef;
import com.example.ClaimShieldX.rules.model.InputReliability;
import com.example.ClaimShieldX.rules.model.RuleOutcome;
import com.example.ClaimShieldX.rules.model.RuleSeverity;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One rule's result. {@code id} is stable and is what a snapshot or finding cites. {@code values} holds the numbers, dates and ids the explanation is built from (amounts as plain decimal
 * strings); {@code evidence} points at where they came from. Quotes inside evidence are document text and must be shown as plain
 * text, never as markup.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RuleResultResponse(UUID id, String ruleId, String ruleVersion, String label, RuleOutcome outcome, RuleSeverity severity, InputReliability reliability,
                                 List<String> reliabilityReasons, String explanation, Map<String, Object> values, List<EvidenceRef> evidence,
                                 List<String> considerations, Instant executedAt) {
}
