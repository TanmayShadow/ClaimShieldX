package com.example.ClaimShieldX.rules.model;

import java.util.List;
import java.util.Map;

/**
 * The outcome of one rule for one set of inputs. Everything here is derived from the inputs, so the same inputs and rule
 * version always give an equal result. When it was executed is not part of the result: the service stamps that when the
 * run is stored.
 *
 * @param values the structured numbers, dates and ids the explanation is built from (amounts as plain decimal strings)
 * @param considerations neutral alternative explanations that a reviewer should rule out; present on FLAG only
 */
public record RuleResult(String ruleId, String ruleVersion, RuleOutcome outcome, RuleSeverity severity, InputReliability reliability,
                         List<String> reliabilityReasons, String explanation, Map<String, Object> values,
                         List<EvidenceRef> evidence, List<String> considerations) {
    public RuleResult {
        if ((outcome == RuleOutcome.FLAG) != (severity != null)) {
            throw new IllegalArgumentException("A severity is given exactly when the outcome is FLAG");
        }
        reliabilityReasons = List.copyOf(reliabilityReasons);
        values = values == null ? Map.of() : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(values));
        evidence = List.copyOf(evidence);
        considerations = List.copyOf(considerations);
    }
}
