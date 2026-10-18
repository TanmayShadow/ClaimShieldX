package com.example.ClaimShieldX.rules.engine;

import com.example.ClaimShieldX.rules.config.RuleProperties;
import com.example.ClaimShieldX.rules.model.RuleDefinition;
import com.example.ClaimShieldX.rules.model.RuleResult;

/** A deterministic check. Implementations must not read the clock, the database or anything outside their arguments. */
public interface Rule {
    RuleDefinition definition();

    RuleResult evaluate(RuleInputs inputs, RuleProperties config);
}
