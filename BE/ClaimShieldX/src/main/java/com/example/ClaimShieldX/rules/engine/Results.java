package com.example.ClaimShieldX.rules.engine;

import com.example.ClaimShieldX.rules.model.EvidenceRef;
import com.example.ClaimShieldX.rules.model.InputReliability;
import com.example.ClaimShieldX.rules.model.RuleDefinition;
import com.example.ClaimShieldX.rules.model.RuleOutcome;
import com.example.ClaimShieldX.rules.model.RuleResult;
import com.example.ClaimShieldX.rules.model.RuleSeverity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small helpers that keep every rule's result built the same way. */
final class Results {
    private Results() {
    }

    /** Money is compared and shown at two decimals, rounded half up, in every rule. */
    static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    static String plain(BigDecimal value) {
        return money(value).toPlainString();
    }

    static RuleResult notApplicable(RuleDefinition rule, String explanation, Map<String, Object> values) {
        return new RuleResult(rule.id(), rule.version(), RuleOutcome.NOT_APPLICABLE, null, InputReliability.RELIABLE, List.of(), explanation, values, List.of(), List.of());
    }

    static RuleResult incomplete(RuleDefinition rule, String explanation, List<String> missing, Map<String, Object> values, List<EvidenceRef> evidence) {
        Map<String, Object> all = new LinkedHashMap<>(values);
        all.put("missing", List.copyOf(missing));
        return new RuleResult(rule.id(), rule.version(), RuleOutcome.INCOMPLETE, null, InputReliability.RELIABLE, List.of(), explanation, all, evidence, List.of());
    }

    static RuleResult pass(RuleDefinition rule, InputReliability reliability, List<String> doubts, String explanation, Map<String, Object> values, List<EvidenceRef> evidence) {
        return new RuleResult(rule.id(), rule.version(), RuleOutcome.PASS, null, reliability, doubts, explanation, values, evidence, List.of());
    }

    static RuleSeverity severity(boolean material) {
        return material ? RuleSeverity.REVIEW : RuleSeverity.INFO;
    }

    /** Material only if the inputs the deviation rests on can be relied on. */
    static RuleSeverity severity(boolean material, List<String> doubts) {
        return severity(material && doubts.isEmpty());
    }

    /**
     * A flag is REVIEW only when the deviation is material: beyond tolerance, no benign formatting explanation, and read from
     * a document that can be relied on. Otherwise it is recorded as INFO so it is visible but not weighed as a finding.
     */
    static RuleResult flag(RuleDefinition rule, RuleSeverity severity, InputReliability reliability, List<String> doubts, String explanation,
                           Map<String, Object> values, List<EvidenceRef> evidence) {
        return new RuleResult(rule.id(), rule.version(), RuleOutcome.FLAG, severity, reliability, doubts, explanation, values, evidence, rule.considerations());
    }

    static RuleResult error(RuleDefinition rule, Exception cause) {
        return new RuleResult(rule.id(), rule.version(), RuleOutcome.ERROR, null, InputReliability.RELIABLE, List.of(),
                "This rule could not be evaluated because of an internal error. No conclusion is drawn from it.",
                Map.of("errorType", cause.getClass().getSimpleName()), List.of(), List.of());
    }

    static Map<String, Object> values() {
        return new LinkedHashMap<>();
    }

    static <T> List<T> listOf(List<T> first, List<T> second) {
        List<T> all = new ArrayList<>(first);
        all.addAll(second);
        return all;
    }
}
