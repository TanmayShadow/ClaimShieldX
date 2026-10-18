package com.example.ClaimShieldX.rules.dto;

import java.util.List;
import java.util.Map;

/** The rules that exist, what each needs, and the thresholds currently in force. */
public record RuleCatalogueResponse(String ruleSetVersion, List<RuleInfo> rules, Map<String, Object> configuration) {
    public record RuleInfo(String ruleId, String version, String label, String description, List<String> needs, List<String> considerations) {
    }
}
