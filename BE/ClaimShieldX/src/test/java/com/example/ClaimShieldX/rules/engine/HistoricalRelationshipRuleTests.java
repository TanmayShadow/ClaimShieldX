package com.example.ClaimShieldX.rules.engine;

import com.example.ClaimShieldX.rules.config.RuleProperties;
import com.example.ClaimShieldX.rules.engine.RuleInputs.HistoryFacts;
import com.example.ClaimShieldX.rules.model.InputReliability;
import com.example.ClaimShieldX.rules.model.RuleOutcome;
import com.example.ClaimShieldX.rules.model.RuleResult;
import com.example.ClaimShieldX.rules.model.RuleSeverity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class HistoricalRelationshipRuleTests {
    private final HistoricalRelationshipRule rule = new HistoricalRelationshipRule();

    private RuleResult run(HistoryFacts history) {
        return rule.evaluate(Inputs.inputs().history(history).build(), RuleProperties.defaults());
    }

    @Test
    void noImportedHistoryIsIncompleteNotNoMatch() {
        RuleResult result = run(Inputs.noHistory());

        assertThat(result.outcome()).isEqualTo(RuleOutcome.INCOMPLETE);
        assertThat(result.explanation()).contains("not the same as finding no match");
        assertThat(result.values().get("missing")).isEqualTo(List.of("HISTORICAL_CLAIMS_IMPORT"));
    }

    @Test
    void aFailedImportWithNothingStoredIsIncomplete() {
        assertThat(run(Inputs.history("HISTORY_IMPORT_FAILED")).outcome()).isEqualTo(RuleOutcome.INCOMPLETE);
    }

    @Test
    void historyThatExistsButHasNoMatchPasses() {
        RuleResult result = run(Inputs.history("NO_MATCH_FOUND"));

        assertThat(result.outcome()).isEqualTo(RuleOutcome.PASS);
        assertThat(result.reliability()).isEqualTo(InputReliability.RELIABLE);
    }

    @Test
    void aSameVehicleClaimWithinTheWindowIsMaterial() {
        RuleResult result = run(Inputs.history("MATCHES_FOUND", Inputs.historyMatch("SAME_VEHICLE", "EXACT", "EARLIER", 45)));

        assertThat(result.outcome()).isEqualTo(RuleOutcome.FLAG);
        assertThat(result.severity()).isEqualTo(RuleSeverity.REVIEW);
        assertThat(result.evidence()).singleElement().satisfies(e -> {
            assertThat(e.rowNumber()).isEqualTo(7);
            assertThat(e.sourceRecordId()).isEqualTo("HC-0007");
        });
        assertThat(result.explanation()).contains("not a finding").startsWith("1 earlier claim in the imported history relates to this claim.");
        assertThat(result.considerations()).isNotEmpty();
    }

    @Test
    void theWindowIsInclusiveAt365DaysAndAnEarlierClaimBeyondItIsInformationOnly() {
        assertThat(run(Inputs.history("MATCHES_FOUND", Inputs.historyMatch("SAME_VEHICLE", "EXACT", "EARLIER", 365))).severity()).isEqualTo(RuleSeverity.REVIEW);
        assertThat(run(Inputs.history("MATCHES_FOUND", Inputs.historyMatch("SAME_VEHICLE", "EXACT", "EARLIER", 366))).severity()).isEqualTo(RuleSeverity.INFO);
    }

    @Test
    void aSameDayClaimOnTheSameVehicleIsMaterialAndALaterOneIsNot() {
        assertThat(run(Inputs.history("MATCHES_FOUND", Inputs.historyMatch("SAME_VEHICLE", "EXACT", "SAME_DAY", 0))).severity()).isEqualTo(RuleSeverity.REVIEW);
        assertThat(run(Inputs.history("MATCHES_FOUND", Inputs.historyMatch("SAME_VEHICLE", "EXACT", "LATER", 20))).severity()).isEqualTo(RuleSeverity.INFO);
    }

    @Test
    void policyOrClaimantTokensAloneAreContextNotMaterial() {
        RuleResult result = run(Inputs.history("MATCHES_FOUND", Inputs.historyMatch("SAME_POLICY", "CONTEXT", "EARLIER", 10), Inputs.historyMatch("SAME_CLAIMANT", "CONTEXT", "EARLIER", 20)));

        assertThat(result.outcome()).isEqualTo(RuleOutcome.FLAG);
        assertThat(result.severity()).isEqualTo(RuleSeverity.INFO);
        assertThat(result.values().get("byRelation")).isEqualTo(Map.of("SAME_CLAIMANT", 1, "SAME_POLICY", 1));
        assertThat(result.explanation()).contains("context");
    }

    @Test
    void anExactMatchAmongContextMatchesDecidesTheSeverityButNotTwice() {
        RuleResult result = run(Inputs.history("MATCHES_FOUND", Inputs.historyMatch("SAME_POLICY", "CONTEXT", "EARLIER", 10), Inputs.historyMatch("SAME_VEHICLE", "EXACT", "EARLIER", 30)));

        assertThat(result.severity()).isEqualTo(RuleSeverity.REVIEW);
        assertThat(result.values()).containsEntry("totalPriorClaims", 2);
    }

    @Test
    void theListedMatchesAreCappedButTheCountIsComplete() {
        List<HistoryFacts.HistoryMatch> many = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            many.add(Inputs.historyMatch("SAME_VEHICLE", "EXACT", "EARLIER", 100 + i));
        }
        RuleResult result = run(Inputs.history("MATCHES_FOUND", many.toArray(new HistoryFacts.HistoryMatch[0])));

        assertThat(result.values()).containsEntry("totalPriorClaims", 25).containsEntry("listedMatches", 10);
        assertThat(result.evidence()).hasSize(10);
        assertThat(result.explanation()).contains("25 earlier claims");
    }

    @Test
    void aFailedLatestImportMakesTheResultDoubtfulAndNotMaterial() {
        HistoryFacts facts = new HistoryFacts("MATCHES_FOUND", UUID.randomUUID(), true, 1, false, List.of(Inputs.historyMatch("SAME_VEHICLE", "EXACT", "EARLIER", 5)));

        RuleResult result = run(facts);

        assertThat(result.severity()).isEqualTo(RuleSeverity.INFO);
        assertThat(result.reliability()).isEqualTo(InputReliability.DOUBTFUL);
        assertThat(result.reliabilityReasons()).containsExactly("LATEST_HISTORY_IMPORT_FAILED");
    }

    @Test
    void aTruncatedLookupIsDisclosed() {
        HistoryFacts facts = new HistoryFacts("MATCHES_FOUND", UUID.randomUUID(), false, 600, true, List.of(Inputs.historyMatch("SAME_VEHICLE", "EXACT", "EARLIER", 5)));

        assertThat(run(facts).values()).containsEntry("listPossiblyIncomplete", true);
    }

    @Test
    void theRecentWindowIsConfigurable() {
        RuleProperties narrow = new RuleProperties(RuleProperties.defaults().timeZone(), new BigDecimal("1.00"), new BigDecimal("0.01"), new BigDecimal("10.00"), new BigDecimal("1000.00"), 0, 180,
                new BigDecimal("0.80"), 2, 30, 10);
        RuleInputs inputs = Inputs.inputs().history(Inputs.history("MATCHES_FOUND", Inputs.historyMatch("SAME_VEHICLE", "EXACT", "EARLIER", 45))).build();

        assertThat(rule.evaluate(inputs, narrow).severity()).isEqualTo(RuleSeverity.INFO);
        assertThat(rule.evaluate(inputs, RuleProperties.defaults()).severity()).isEqualTo(RuleSeverity.REVIEW);
    }

    @Test
    void theSameInputsGiveTheSameResult() {
        RuleInputs inputs = Inputs.inputs().history(Inputs.history("MATCHES_FOUND", Inputs.historyMatch("SAME_VEHICLE", "EXACT", "EARLIER", 45))).build();

        assertThat(rule.evaluate(inputs, RuleProperties.defaults())).isEqualTo(rule.evaluate(inputs, RuleProperties.defaults()));
    }
}
