package com.example.ClaimShieldX.analysis;

import com.example.ClaimShieldX.analysis.findings.ScoreCalculator;
import com.example.ClaimShieldX.analysis.findings.ScoreProperties;
import com.example.ClaimShieldX.analysis.snapshot.SnapshotService.Item;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The review-priority score: transparent points per rule result, never a probability, never a verdict. */
class ScoreCalculatorTests {
    private final ScoreCalculator calculator = new ScoreCalculator(ScoreProperties.defaults());

    private static Item rule(String ruleId, String outcome, String severity) {
        return new Item("RULE." + ruleId, "RULE_RESULT", "DETERMINISTIC_RESULT", ruleId,
                severity == null ? Map.of("ruleId", ruleId, "outcome", outcome) : Map.of("ruleId", ruleId, "outcome", outcome, "severity", severity));
    }

    private ScoreCalculator.Score score(Item... items) {
        return calculator.calculate(List.of(items));
    }

    @Test
    void nothingFlaggedAndNothingMissingScoresZeroAndAsksForNothing() {
        var s = score(rule("INVOICE_ARITHMETIC", "PASS", null), rule("DATE_CONSISTENCY", "NOT_APPLICABLE", null));
        assertThat(s.total()).isZero();
        assertThat(s.band()).isEqualTo("LOW");
        assertThat(s.recommendedAction()).isEqualTo("NO_ADDITIONAL_CHECK_INDICATED");
        assertThat(s.components()).isEmpty();
    }

    @Test
    void checksThatCouldNotRunAddNoPointsButAskForMoreEvidence() {
        var s = score(rule("INVOICE_ARITHMETIC", "INCOMPLETE", null), rule("DATE_CONSISTENCY", "PASS", null));
        assertThat(s.total()).isZero();
        assertThat(s.recommendedAction()).isEqualTo("REQUEST_MORE_EVIDENCE");
    }

    @Test
    void aMaterialFlagAddsItsPointsAndRecommendsAManualReview() {
        var s = score(rule("INVOICE_ARITHMETIC", "FLAG", "REVIEW"));
        assertThat(s.total()).isEqualTo(15);
        assertThat(s.band()).isEqualTo("LOW");
        assertThat(s.recommendedAction()).isEqualTo("MANUAL_REVIEW");
    }

    @Test
    void aNotedFlagAddsFewerPointsThanAMaterialOne() {
        assertThat(score(rule("ESTIMATE_INVOICE_VARIANCE", "FLAG", "INFO")).total()).isEqualTo(5);
        assertThat(score(rule("ESTIMATE_INVOICE_VARIANCE", "FLAG", "REVIEW")).total()).isEqualTo(25);
    }

    @Test
    void aNotedFlagAloneDoesNotDemandAManualReview() {
        assertThat(score(rule("REPEATED_LINE_ITEMS", "FLAG", "INFO")).recommendedAction()).isEqualTo("NO_ADDITIONAL_CHECK_INDICATED");
    }

    @Test
    void theTwoDuplicateSignalsDescribeOneFactSoOnlyTheLargerCounts() {
        var s = score(rule("DOCUMENT_FILE_DUPLICATE", "FLAG", "REVIEW"), rule("INVOICE_IDENTITY_DUPLICATE", "FLAG", "INFO"));
        assertThat(s.total()).isEqualTo(30);
        assertThat(s.components()).singleElement().satisfies(c -> assertThat(c.note()).contains("only the larger"));
    }

    @Test
    void severalDifferentFlagsAddUpAndBandsFollowTheThresholds() {
        var medium = score(rule("INVOICE_ARITHMETIC", "FLAG", "REVIEW"), rule("DATE_CONSISTENCY", "FLAG", "INFO"), rule("REPEATED_LINE_ITEMS", "FLAG", "INFO"));
        assertThat(medium.total()).isEqualTo(20);
        assertThat(medium.band()).isEqualTo("MEDIUM");
        assertThat(medium.recommendedAction()).isEqualTo("MANUAL_REVIEW");

        var high = score(rule("INVOICE_IDENTITY_DUPLICATE", "FLAG", "REVIEW"), rule("ESTIMATE_INVOICE_VARIANCE", "FLAG", "REVIEW"));
        assertThat(high.total()).isEqualTo(55);
        assertThat(high.band()).isEqualTo("HIGH");
    }

    @Test
    void theTotalNeverExceedsOneHundred() {
        List<Item> everything = new ArrayList<>();
        for (String id : List.of("DOCUMENT_FILE_DUPLICATE", "ESTIMATE_INVOICE_VARIANCE", "HISTORICAL_RELATIONSHIP", "INVOICE_ARITHMETIC", "DATE_CONSISTENCY", "REPEATED_LINE_ITEMS")) {
            everything.add(rule(id, "FLAG", "REVIEW"));
        }
        var props = new ScoreProperties("1", Map.of("INVOICE_ARITHMETIC", 100, "DATE_CONSISTENCY", 100), null, 20, 50);
        assertThat(new ScoreCalculator(props).calculate(everything).total()).isEqualTo(100);
    }

    @Test
    void onlyRuleResultsScoreSoModelHypothesesAndSharedGaragesAddNothing() {
        var s = score(new Item("HIST.abc.1", "HISTORICAL_RECORD", "CONTEXT", "History", Map.of("outcome", "FLAG", "severity", "REVIEW", "ruleId", "INVOICE_ARITHMETIC")),
                new Item("GAP.DATE_CONSISTENCY", "UNKNOWN", "UNKNOWN", "Gap", Map.of()));
        assertThat(s.total()).isZero();
    }

    @Test
    void componentsAreOrderedByPointsThenName() {
        var s = score(rule("DATE_CONSISTENCY", "FLAG", "REVIEW"), rule("INVOICE_ARITHMETIC", "FLAG", "REVIEW"), rule("HISTORICAL_RELATIONSHIP", "FLAG", "REVIEW"));
        assertThat(s.components()).extracting(ScoreCalculator.Component::ruleId).containsExactly("HISTORICAL_RELATIONSHIP", "DATE_CONSISTENCY", "INVOICE_ARITHMETIC");
    }

    @Test
    void settingsThatCannotWorkAreRefusedAtStartup() {
        assertThatThrownBy(() -> new ScoreProperties("1", Map.of("INVOICE_ARITHMETIC", 101), null, 20, 50)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ScoreProperties("1", null, null, 50, 50)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ScoreProperties("1", null, null, 0, 50)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ScoreProperties("1", null, Map.of("X", -1), 20, 50)).isInstanceOf(IllegalArgumentException.class);
    }
}
