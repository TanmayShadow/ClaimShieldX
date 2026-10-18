package com.example.ClaimShieldX.rules.engine;

import com.example.ClaimShieldX.documents.model.DocumentType;
import com.example.ClaimShieldX.extraction.schema.IssueCode;
import com.example.ClaimShieldX.readiness.model.Readiness;
import com.example.ClaimShieldX.readiness.model.RequirementLevel;
import com.example.ClaimShieldX.rules.config.RuleProperties;
import com.example.ClaimShieldX.rules.model.InputReliability;
import com.example.ClaimShieldX.rules.model.RuleOutcome;
import com.example.ClaimShieldX.rules.model.RuleResult;
import com.example.ClaimShieldX.rules.model.RuleSeverity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EstimateInvoiceVarianceRuleTests {
    private final EstimateInvoiceVarianceRule rule = new EstimateInvoiceVarianceRule();

    private RuleResult run(Inputs.Builder inputs) {
        return rule.evaluate(inputs.build(), RuleProperties.defaults());
    }

    private static Inputs.Builder with(String invoiceTotal, String estimateTotal) {
        return Inputs.inputs().with(Inputs.invoice().set("grandTotal", invoiceTotal), Inputs.estimate().set("grandTotal", estimateTotal));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> comparisons(RuleResult result) {
        return (List<Map<String, Object>>) result.values().get("comparisons");
    }

    @Test
    void anInvoiceSlightlyAboveTheEstimatePasses() {
        RuleResult result = run(with("15930.00", "15000.00"));

        assertThat(result.outcome()).isEqualTo(RuleOutcome.PASS);
        assertThat(result.values()).containsEntry("primaryBasis", "GARAGE_ESTIMATE_DOCUMENT");
        assertThat(comparisons(result).get(0)).containsEntry("difference", "930.00").containsEntry("percent", "6.20").containsEntry("direction", "INCREASE").containsEntry("exceedsThreshold", false);
    }

    @Test
    void exactlyTheThresholdPercentageIsNotFlaggedButJustAboveIs() {
        assertThat(run(with("16500.00", "15000.00")).outcome()).isEqualTo(RuleOutcome.PASS);
        RuleResult above = run(with("16501.00", "15000.00"));
        assertThat(above.outcome()).isEqualTo(RuleOutcome.FLAG);
        assertThat(above.severity()).isEqualTo(RuleSeverity.REVIEW);
        assertThat(comparisons(above).get(0)).containsEntry("percent", "10.01").containsEntry("difference", "1501.00");
        assertThat(above.considerations()).anyMatch(c -> c.contains("Supplementary damage"));
        assertThat(above.explanation()).contains("above the garage estimate").doesNotContain("fraud");
    }

    @Test
    void aLargePercentageOnASmallBillIsNotFlaggedUntilTheMinimumIncreaseIsReached() {
        assertThat(run(with("1500.00", "1000.00")).outcome()).isEqualTo(RuleOutcome.PASS);
        assertThat(run(with("3000.00", "2000.00")).outcome()).isEqualTo(RuleOutcome.FLAG);
        assertThat(run(with("2999.99", "2000.00")).outcome()).isEqualTo(RuleOutcome.PASS);
    }

    @Test
    void aLargeAmountWithASmallPercentageIsNotFlagged() {
        assertThat(run(with("109000.00", "100000.00")).outcome()).isEqualTo(RuleOutcome.PASS);
    }

    @Test
    void aDecreaseIsNeverFlagged() {
        RuleResult result = run(with("5000.00", "15000.00"));

        assertThat(result.outcome()).isEqualTo(RuleOutcome.PASS);
        assertThat(comparisons(result).get(0)).containsEntry("direction", "DECREASE").containsEntry("difference", "-10000.00").containsEntry("percent", "-66.67");
        assertThat(result.explanation()).contains("below");
    }

    @Test
    void withoutAnEstimateDocumentTheClaimsInitialEstimateIsTheBaseline() {
        RuleResult result = run(Inputs.inputs().with(Inputs.invoice().set("grandTotal", "30000.00")));

        assertThat(result.outcome()).isEqualTo(RuleOutcome.FLAG);
        assertThat(result.values()).containsEntry("primaryBasis", "CLAIM_INITIAL_ESTIMATE");
        assertThat(result.evidence()).anyMatch(e -> "financial.initialEstimateAmount".equals(e.claimField()));
        assertThat(result.reliability()).isEqualTo(InputReliability.RELIABLE);
    }

    @Test
    void withoutAnythingElseTheApprovedEstimateIsTheBaseline() {
        RuleResult result = run(Inputs.inputs().claim(Inputs.claimB().initial(null).approved("20000.00").build()).with(Inputs.invoice().set("grandTotal", "30000.00")));

        assertThat(result.outcome()).isEqualTo(RuleOutcome.FLAG);
        assertThat(result.values()).containsEntry("primaryBasis", "CLAIM_APPROVED_ESTIMATE");
    }

    @Test
    void withNoBaselineAtAllTheRuleIsIncomplete() {
        RuleResult result = run(Inputs.inputs().claim(Inputs.claimB().initial(null).build()).with(Inputs.invoice()));

        assertThat(result.outcome()).isEqualTo(RuleOutcome.INCOMPLETE);
        assertThat(result.values().get("missing")).isEqualTo(List.of("GARAGE_ESTIMATE_OR_CLAIM_ESTIMATE_AMOUNT"));
    }

    @Test
    void onlyOneBaselineDecidesTheOutcomeSoTheSameIncreaseIsNotCountedTwice() {
        RuleResult result = run(Inputs.inputs().claim(Inputs.claimB().approved("10000.00").build()).with(Inputs.invoice().set("grandTotal", "15930.00"), Inputs.estimate()));

        assertThat(result.outcome()).isEqualTo(RuleOutcome.PASS);
        assertThat(comparisons(result)).hasSize(3);
        assertThat(comparisons(result)).filteredOn(c -> Boolean.TRUE.equals(c.get("decidesOutcome"))).extracting(c -> c.get("basis")).containsExactly("GARAGE_ESTIMATE_DOCUMENT");
        assertThat(comparisons(result)).filteredOn(c -> "CLAIM_APPROVED_ESTIMATE".equals(c.get("basis"))).extracting(c -> c.get("exceedsThreshold")).containsExactly(true);
    }

    @Test
    void aBaselineInAnotherCurrencyIsSkippedNeverConverted() {
        RuleResult result = run(Inputs.inputs().with(Inputs.invoice().set("grandTotal", "30000.00"), Inputs.estimate().set("currency", "USD")));

        assertThat(result.values()).containsEntry("primaryBasis", "CLAIM_INITIAL_ESTIMATE");
        assertThat((List<?>) result.values().get("skippedBaselines")).hasSize(1);
    }

    @Test
    void ifEveryBaselineIsInAnotherCurrencyTheRuleIsIncomplete() {
        RuleResult result = run(Inputs.inputs().claim(Inputs.claimB().currency("USD").build()).with(Inputs.invoice().set("grandTotal", "30000.00"), Inputs.estimate().set("currency", "USD")));

        assertThat(result.outcome()).isEqualTo(RuleOutcome.INCOMPLETE);
    }

    @Test
    void anEstimateThatWasNeverReadFallsBackToTheClaimRecord() {
        RuleResult result = run(Inputs.inputs().with(Inputs.invoice().set("grandTotal", "30000.00"), Inputs.estimate().notExtracted()));

        assertThat(result.values()).containsEntry("primaryBasis", "CLAIM_INITIAL_ESTIMATE");
        assertThat((List<Map<String, Object>>) result.values().get("skippedBaselines")).extracting(m -> m.get("reason")).containsExactly("ESTIMATE_NOT_READ");
    }

    @Test
    void anUnreadableInvoiceTotalOrCurrencyIsIncompleteNotZero() {
        assertThat(run(Inputs.inputs().with(Inputs.invoice().unreadable("grandTotal"), Inputs.estimate())).outcome()).isEqualTo(RuleOutcome.INCOMPLETE);
        assertThat(run(Inputs.inputs().with(Inputs.invoice().unreadable("currency"), Inputs.estimate())).outcome()).isEqualTo(RuleOutcome.INCOMPLETE);
    }

    @Test
    void aSupplementaryEstimateIsNoted() {
        RuleResult result = run(with("30000.00", "15000.00").with(Inputs.plain(DocumentType.SUPPLEMENTARY_ESTIMATE)));

        assertThat(result.outcome()).isEqualTo(RuleOutcome.FLAG);
        assertThat(result.values()).containsEntry("supplementaryEstimateReceived", true);
        assertThat(result.explanation()).contains("supplementary estimate");
    }

    @Test
    void anInvoiceWhoseOwnTotalsDoNotReconcileIsNotMaterialHere() {
        RuleResult result = run(Inputs.inputs().with(Inputs.invoice().set("grandTotal", "30000.00").issue(IssueCode.GRAND_TOTAL_MISMATCH), Inputs.estimate()));

        assertThat(result.outcome()).isEqualTo(RuleOutcome.FLAG);
        assertThat(result.severity()).isEqualTo(RuleSeverity.INFO);
        assertThat(result.reliabilityReasons()).containsExactly("GRAND_TOTAL_MISMATCH");
    }

    @Test
    void aDoubtfulEstimateAsTheBaselineIsNotMaterial() {
        RuleResult result = run(Inputs.inputs().with(Inputs.invoice().set("grandTotal", "30000.00"), Inputs.estimate().issue(IssueCode.IDENTIFIER_MISMATCH)));

        assertThat(result.severity()).isEqualTo(RuleSeverity.INFO);
        assertThat(result.reliabilityReasons()).containsExactly("IDENTIFIER_MISMATCH");
    }

    @Test
    void aZeroBaselineHasNoPercentageButAnIncreaseOfTheMinimumIsStillFlagged() {
        RuleResult result = run(with("5000.00", "0.00"));

        assertThat(result.outcome()).isEqualTo(RuleOutcome.FLAG);
        assertThat(comparisons(result).get(0).get("percent")).isNull();
    }

    @Test
    void noInvoiceIsIncompleteAndAnUnexpectedInvoiceIsNotApplicable() {
        assertThat(run(Inputs.inputs().with(Inputs.estimate())).outcome()).isEqualTo(RuleOutcome.INCOMPLETE);
        assertThat(run(Inputs.inputs().checklist(Inputs.checklist(Readiness.READY, List.of(Inputs.entry(DocumentType.FINAL_INVOICE, RequirementLevel.NOT_APPLICABLE)), List.of()))).outcome())
                .isEqualTo(RuleOutcome.NOT_APPLICABLE);
    }

    @Test
    void thresholdsComeFromConfiguration() {
        RuleProperties strict = new RuleProperties(RuleProperties.defaults().timeZone(), new BigDecimal("1.00"), new BigDecimal("0.01"), new BigDecimal("5.00"), new BigDecimal("100.00"), 0, 180,
                new BigDecimal("0.80"), 2, 365, 10);
        RuleInputs inputs = with("15930.00", "15000.00").build();

        assertThat(rule.evaluate(inputs, strict).outcome()).isEqualTo(RuleOutcome.FLAG);
        assertThat(rule.evaluate(inputs, RuleProperties.defaults()).outcome()).isEqualTo(RuleOutcome.PASS);
    }

    @Test
    void theSameInputsGiveTheSameResult() {
        RuleInputs inputs = with("16501.00", "15000.00").build();

        assertThat(rule.evaluate(inputs, RuleProperties.defaults())).isEqualTo(rule.evaluate(inputs, RuleProperties.defaults()));
    }
}
