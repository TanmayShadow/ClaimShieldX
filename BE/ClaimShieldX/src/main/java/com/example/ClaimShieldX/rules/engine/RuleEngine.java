package com.example.ClaimShieldX.rules.engine;

import com.example.ClaimShieldX.rules.config.RuleProperties;
import com.example.ClaimShieldX.rules.model.RuleDefinition;
import com.example.ClaimShieldX.rules.model.RuleResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs every rule on the same inputs, in a fixed order, and never lets one rule's failure hide the others: an exception becomes
 * an ERROR result for that rule alone. The rule set has a version; adding, removing or changing the behaviour of a rule means a
 * new version of that rule and of the set, so an old run can always be explained.
 */
@Component
public final class RuleEngine {
    /** Version of the whole set. Bump it whenever a rule is added, removed or its version changes. */
    public static final String RULE_SET_VERSION = "1";

    private static final Logger log = LoggerFactory.getLogger(RuleEngine.class);

    private final List<Rule> rules;

    public RuleEngine() {
        this(List.of(new RequiredEvidenceRule(), new InvoiceArithmeticRule(), new EstimateInvoiceVarianceRule(), new DateConsistencyRule(),
                new DocumentFileDuplicateRule(), new InvoiceIdentityDuplicateRule(), new RepeatedLineItemsRule(), new HistoricalRelationshipRule()));
    }

    RuleEngine(List<Rule> rules) {
        this.rules = List.copyOf(rules);
    }

    public List<RuleDefinition> definitions() {
        return rules.stream().map(Rule::definition).toList();
    }

    public List<RuleResult> evaluate(RuleInputs inputs, RuleProperties config) {
        List<RuleResult> results = new ArrayList<>();
        for (Rule rule : rules) {
            try {
                results.add(rule.evaluate(inputs, config));
            } catch (RuntimeException e) {
                log.error("Rule {} failed with {} for claim {}", rule.definition().id(), e.getClass().getSimpleName(), inputs.claim().id());
                results.add(Results.error(rule.definition(), e));
            }
        }
        return List.copyOf(results);
    }
}
