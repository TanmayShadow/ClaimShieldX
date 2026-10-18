package com.example.ClaimShieldX.rules.model;

import java.util.List;

/** What a rule is called, what it needs, and the benign explanations to rule out when it flags something. */
public record RuleDefinition(String id, String version, String label, String description, List<String> needs, List<String> considerations) {
    public RuleDefinition {
        needs = List.copyOf(needs);
        considerations = List.copyOf(considerations);
    }
}
