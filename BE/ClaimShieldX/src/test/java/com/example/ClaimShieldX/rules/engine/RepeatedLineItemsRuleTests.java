package com.example.ClaimShieldX.rules.engine;

import com.example.ClaimShieldX.extraction.schema.IssueCode;
import com.example.ClaimShieldX.rules.config.RuleProperties;
import com.example.ClaimShieldX.rules.model.RuleOutcome;
import com.example.ClaimShieldX.rules.model.RuleResult;
import com.example.ClaimShieldX.rules.model.RuleSeverity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RepeatedLineItemsRuleTests {
    private final RepeatedLineItemsRule rule = new RepeatedLineItemsRule();

    private RuleResult run(Inputs.Doc invoice) {
        return rule.evaluate(Inputs.inputs().with(invoice).build(), RuleProperties.defaults());
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> groups(RuleResult result) {
        return (List<Map<String, Object>>) result.values().get("groups");
    }

    @Test
    void distinctLinesPass() {
        RuleResult result = run(Inputs.invoice());

        assertThat(result.outcome()).isEqualTo(RuleOutcome.PASS);
        assertThat(result.values()).containsEntry("lineItems", 2).containsEntry("comparableLines", 2);
    }

    @Test
    void theSameDescriptionAndUnitPriceTwiceIsAnExactRepeat() {
        RuleResult result = run(Inputs.invoice().clearLines().line("Front bumper replacement", "1", "5000.00", "5000.00").line("Headlamp assembly left", "1", "3500.00", "3500.00")
                .line("Front bumper replacement", "2", "5000.00", "10000.00"));

        assertThat(result.outcome()).isEqualTo(RuleOutcome.FLAG);
        assertThat(result.severity()).isEqualTo(RuleSeverity.REVIEW);
        assertThat(groups(result)).hasSize(1);
        assertThat(groups(result).get(0)).containsEntry("kind", "EXACT").containsEntry("amountOfRepeatedLines", "10000.00");
        assertThat(result.values()).containsEntry("amountOfRepeatedLines", "10000.00");
        assertThat(result.evidence()).hasSize(2);
        assertThat(result.considerations()).isNotEmpty();
    }

    @Test
    void punctuationAndCaseDoNotHideARepeat() {
        RuleResult result = run(Inputs.invoice().clearLines().line("Front Bumper - Replacement", "1", "5000.00", "5000.00").line("front bumper replacement.", "1", "5000.00", "5000.00"));

        assertThat(groups(result).get(0)).containsEntry("kind", "EXACT");
    }

    @Test
    void sameWordsInAnotherOrderWithTheSamePriceIsOnlyNearIdentical() {
        RuleResult result = run(Inputs.invoice().clearLines().line("Bumper front replacement", "1", "5000.00", "5000.00").line("Front bumper replacement", "1", "5000.00", "5000.00"));

        assertThat(result.outcome()).isEqualTo(RuleOutcome.FLAG);
        assertThat(result.severity()).isEqualTo(RuleSeverity.INFO);
        assertThat(groups(result).get(0)).containsEntry("kind", "NEAR");
        assertThat(result.explanation()).contains("near-identical");
    }

    @Test
    void descriptionsSharingEnoughWordsAreNearIdenticalOnlyAtTheThreshold() {
        // 4 shared of 5 distinct words = 0.80, which reaches the default threshold.
        RuleResult at = run(Inputs.invoice().clearLines().line("Front bumper assembly replacement left", "1", "5000.00", "5000.00").line("Front bumper assembly replacement", "1", "5000.00", "5000.00"));
        // 3 shared of 4 distinct words = 0.75, which does not.
        RuleResult below = run(Inputs.invoice().clearLines().line("Front bumper replacement kit", "1", "5000.00", "5000.00").line("Front bumper replacement", "1", "5000.00", "5000.00"));

        assertThat(at.outcome()).isEqualTo(RuleOutcome.FLAG);
        assertThat(below.outcome()).isEqualTo(RuleOutcome.PASS);
    }

    @Test
    void theSameDescriptionAtADifferentPriceButTheSameLineAmountIsNear() {
        RuleResult result = run(Inputs.invoice().clearLines().line("Rear door panel repair", "2", "2500.00", "5000.00").line("Rear door panel repair", "1", "5000.00", "5000.00"));

        assertThat(groups(result).get(0)).containsEntry("kind", "NEAR");
    }

    @Test
    void theSameDescriptionAtADifferentPriceAndAmountIsNotARepeat() {
        assertThat(run(Inputs.invoice().clearLines().line("Rear door panel repair", "1", "2500.00", "2500.00").line("Rear door panel repair", "1", "4000.00", "4000.00")).outcome()).isEqualTo(RuleOutcome.PASS);
    }

    @Test
    void aGenericOneWordDescriptionIsNotComparedAndOtherLinesStillAre() {
        RuleResult result = run(Inputs.invoice().clearLines().line("Labour", "1", "1000.00", "1000.00").line("Labour", "1", "1000.00", "1000.00")
                .line("Front bumper replacement", "1", "5000.00", "5000.00").line("Headlamp assembly left", "1", "3500.00", "3500.00"));

        assertThat(result.outcome()).isEqualTo(RuleOutcome.PASS);
        assertThat(result.values()).containsEntry("lineItems", 4).containsEntry("comparableLines", 2);
    }

    @Test
    void ifTooFewLinesCanBeComparedTheRuleIsIncompleteNotAPass() {
        RuleResult result = run(Inputs.invoice().clearLines().line("Labour", "1", "1000.00", "1000.00").line("Labour", "1", "1000.00", "1000.00"));

        assertThat(result.outcome()).isEqualTo(RuleOutcome.INCOMPLETE);
    }

    @Test
    void theMinimumNumberOfWordsIsConfigurable() {
        RuleProperties loose = new RuleProperties(RuleProperties.defaults().timeZone(), new BigDecimal("1.00"), new BigDecimal("0.01"), new BigDecimal("10.00"), new BigDecimal("1000.00"), 0, 180,
                new BigDecimal("0.80"), 1, 365, 10);
        RuleInputs inputs = Inputs.inputs().with(Inputs.invoice().clearLines().line("Labour", "1", "1000.00", "1000.00").line("Labour", "1", "1000.00", "1000.00")).build();

        assertThat(rule.evaluate(inputs, loose).outcome()).isEqualTo(RuleOutcome.FLAG);
        assertThat(rule.evaluate(inputs, RuleProperties.defaults()).outcome()).isEqualTo(RuleOutcome.INCOMPLETE);
    }

    @Test
    void aLineWithoutAReadableUnitPriceIsNotComparable() {
        RuleResult result = run(Inputs.invoice().clearLines().line("Front bumper replacement", "1", null, "5000.00").line("Front bumper replacement", "1", null, "5000.00"));

        assertThat(result.outcome()).isEqualTo(RuleOutcome.INCOMPLETE);
    }

    @Test
    void oneLineCannotRepeatAndNoLinesIsIncomplete() {
        assertThat(run(Inputs.invoice().clearLines().line("Front bumper replacement", "1", "5000.00", "5000.00")).outcome()).isEqualTo(RuleOutcome.PASS);
        assertThat(run(Inputs.invoice().clearLines()).outcome()).isEqualTo(RuleOutcome.INCOMPLETE);
    }

    @Test
    void threeCopiesFormOneGroupAndTheFirstIsNotCountedAsRepeated() {
        RuleResult result = run(Inputs.invoice().clearLines().line("Wiper blade set", "1", "800.00", "800.00").line("Wiper blade set", "1", "800.00", "800.00").line("Wiper blade set", "1", "800.00", "800.00"));

        assertThat(groups(result)).hasSize(1);
        assertThat(groups(result).get(0)).containsEntry("amountOfRepeatedLines", "1600.00");
        assertThat((List<?>) groups(result).get(0).get("lines")).hasSize(3);
    }

    @Test
    void twoSeparateRepeatsAreTwoGroupsInInvoiceOrder() {
        RuleResult result = run(Inputs.invoice().clearLines().line("Wiper blade set", "1", "800.00", "800.00").line("Tail lamp assembly", "1", "2200.00", "2200.00")
                .line("Tail lamp assembly", "1", "2200.00", "2200.00").line("Wiper blade set", "1", "800.00", "800.00"));

        assertThat(groups(result)).hasSize(2);
        assertThat(result.values()).containsEntry("amountOfRepeatedLines", "3000.00");
        assertThat(((List<Map<String, Object>>) groups(result).get(0).get("lines")).get(0)).containsEntry("description", "Wiper blade set");
    }

    @Test
    void aDoubtfullyReadInvoiceMakesTheRepeatNotMaterialButItsOwnArithmeticIssueDoesNot() {
        Inputs.Doc repeated = Inputs.invoice().clearLines().line("Wiper blade set", "1", "800.00", "800.00").line("Wiper blade set", "1", "800.00", "800.00");

        assertThat(run(repeated.issue(IssueCode.NO_SOURCE_QUOTE)).severity()).isEqualTo(RuleSeverity.INFO);
        Inputs.Doc own = Inputs.invoice().clearLines().line("Wiper blade set", "1", "800.00", "800.00").line("Wiper blade set", "1", "800.00", "800.00").issue(IssueCode.GRAND_TOTAL_MISMATCH);
        assertThat(run(own).severity()).isEqualTo(RuleSeverity.REVIEW);
    }

    @Test
    void noInvoiceAnUnreadInvoiceAndAnUnexpectedInvoice() {
        assertThat(rule.evaluate(Inputs.inputs().build(), RuleProperties.defaults()).outcome()).isEqualTo(RuleOutcome.INCOMPLETE);
        assertThat(run(Inputs.invoice().notExtracted()).outcome()).isEqualTo(RuleOutcome.INCOMPLETE);
    }

    @Test
    void wordsAreLettersAndDigitsInOrderAndSimilarityIsIntersectionOverUnion() {
        assertThat(RepeatedLineItemsRule.words("Front-Bumper (L.H.) #2")).containsExactly("front", "bumper", "l", "h", "2");
        assertThat(RepeatedLineItemsRule.words(null)).isEmpty();
        assertThat(RepeatedLineItemsRule.similarity(List.of("a", "b", "c"), List.of("b", "c", "d"))).isEqualByComparingTo("0.5");
        assertThat(RepeatedLineItemsRule.similarity(List.of("a"), List.of("a"))).isEqualByComparingTo("1");
        assertThat(RepeatedLineItemsRule.similarity(List.of(), List.of())).isEqualByComparingTo("0");
    }

    @Test
    void theSameInputsGiveTheSameResult() {
        Inputs.Doc repeated = Inputs.invoice().clearLines().line("Wiper blade set", "1", "800.00", "800.00").line("Wiper blade set", "1", "800.00", "800.00");
        RuleInputs inputs = Inputs.inputs().with(repeated).build();

        assertThat(rule.evaluate(inputs, RuleProperties.defaults())).isEqualTo(rule.evaluate(inputs, RuleProperties.defaults()));
    }
}
