package com.example.ClaimShieldX.rules.engine;

import com.example.ClaimShieldX.claims.model.ClaimStage;
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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceArithmeticRuleTests {
    private final InvoiceArithmeticRule rule = new InvoiceArithmeticRule();

    private RuleResult run(Inputs.Doc invoice) {
        return rule.evaluate(Inputs.inputs().with(invoice).build(), RuleProperties.defaults());
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> mismatches(RuleResult result) {
        return (List<Map<String, Object>>) result.values().get("mismatches");
    }

    @Test
    void aReconcilingInvoicePasses() {
        RuleResult result = run(Inputs.invoice());

        assertThat(result.outcome()).isEqualTo(RuleOutcome.PASS);
        assertThat(result.severity()).isNull();
        assertThat(result.values()).containsEntry("checksRun", 4).containsEntry("linesChecked", 2).containsEntry("grandTotalFormula", "TAX_ADDED");
        assertThat(result.reliability()).isEqualTo(InputReliability.RELIABLE);
        assertThat(result.considerations()).isEmpty();
    }

    @Test
    void noInvoiceAtTheFinalStageIsIncompleteNotAFinding() {
        RuleResult result = rule.evaluate(Inputs.inputs().build(), RuleProperties.defaults());

        assertThat(result.outcome()).isEqualTo(RuleOutcome.INCOMPLETE);
        assertThat(result.values().get("missing")).isEqualTo(List.of("FINAL_INVOICE"));
    }

    @Test
    void anInvoiceTheChecklistDoesNotExpectIsNotApplicable() {
        RuleResult result = rule.evaluate(Inputs.inputs().claim(Inputs.claim(c -> new com.example.ClaimShieldX.rules.engine.RuleInputs.ClaimFacts(c.id(), c.internalId(), 1,
                        ClaimStage.FNOL, c.incidentType(), c.incidentAt(), c.reportedAt(), c.policyStart(), c.policyEnd(), c.initialEstimate(), null, c.currency(), c.garageId(), c.vehicleToken())))
                .checklist(Inputs.checklist(Readiness.READY, List.of(Inputs.entry(DocumentType.FINAL_INVOICE, RequirementLevel.NOT_APPLICABLE),
                        Inputs.entry(DocumentType.CLAIM_FORM, RequirementLevel.REQUIRED)), List.of())).build(), RuleProperties.defaults());

        assertThat(result.outcome()).isEqualTo(RuleOutcome.NOT_APPLICABLE);
    }

    @Test
    void anInvoiceThatArrivedIsCheckedEvenWhenTheChecklistDoesNotExpectIt() {
        RuleResult result = rule.evaluate(Inputs.inputs().checklist(Inputs.checklist(Readiness.READY, List.of(Inputs.entry(DocumentType.FINAL_INVOICE, RequirementLevel.NOT_APPLICABLE)), List.of()))
                .with(Inputs.invoice()).build(), RuleProperties.defaults());

        assertThat(result.outcome()).isEqualTo(RuleOutcome.PASS);
    }

    @Test
    void anInvoiceThatWasNeverReadIsIncomplete() {
        RuleResult result = run(Inputs.invoice().notExtracted());

        assertThat(result.outcome()).isEqualTo(RuleOutcome.INCOMPLETE);
        assertThat(result.values().get("missing")).isEqualTo(List.of("FINAL_INVOICE_EXTRACTION"));
    }

    @Test
    void aLineThatDoesNotMultiplyOutIsFlaggedAsMaterial() {
        RuleResult result = run(Inputs.invoice().clearLines().line("Front bumper replacement", "2", "5000.00", "12000.00").line("Headlamp assembly left", "1", "3500.00", "3500.00")
                .set("subtotal", "15500.00").set("taxTotal", "2790.00").set("grandTotal", "18290.00"));

        assertThat(result.outcome()).isEqualTo(RuleOutcome.FLAG);
        assertThat(result.severity()).isEqualTo(RuleSeverity.REVIEW);
        assertThat(mismatches(result)).hasSize(1);
        assertThat(mismatches(result).get(0)).containsEntry("check", "LINE_TOTAL").containsEntry("line", 1).containsEntry("expected", "10000.00")
                .containsEntry("printed", "12000.00").containsEntry("difference", "2000.00");
        assertThat(result.considerations()).isNotEmpty();
        assertThat(result.evidence()).isNotEmpty();
        assertThat(result.explanation()).startsWith("1 place on the final invoice does not reconcile after rounding.");
    }

    @Test
    void aUnitPricePrintedToThePaisaMayBeOffByHalfAPaisaPerUnit() {
        Inputs.Doc within = Inputs.invoice().clearLines().line("Washer", "300", "33.33", "10000.00").set("subtotal", "10000.00").set("taxTotal", "0.00").set("grandTotal", "10000.00");
        Inputs.Doc beyond = Inputs.invoice().clearLines().line("Washer", "300", "33.33", "10003.00").set("subtotal", "10003.00").set("taxTotal", "0.00").set("grandTotal", "10003.00");

        assertThat(run(within).outcome()).isEqualTo(RuleOutcome.PASS);
        assertThat(run(beyond).outcome()).isEqualTo(RuleOutcome.FLAG);
    }

    @Test
    void aLineWhoseAmountIncludesItsOwnTaxIsAccepted() {
        RuleResult result = run(Inputs.invoice().clearLines().lineWithTax("Door shell", "2", "1000.00", "360.00", "2360.00").set("subtotal", "2360.00").set("taxTotal", "0.00").set("grandTotal", "2360.00"));

        assertThat(result.outcome()).isEqualTo(RuleOutcome.PASS);
    }

    @Test
    void aWrongSubtotalIsReportedWithTheSumOfTheLines() {
        RuleResult result = run(Inputs.invoice().set("subtotal", "13600.00").set("grandTotal", "16030.00"));

        assertThat(result.outcome()).isEqualTo(RuleOutcome.FLAG);
        assertThat(mismatches(result)).extracting(m -> m.get("check")).containsExactly("SUBTOTAL");
        assertThat(mismatches(result).get(0)).containsEntry("expected", "13500.00").containsEntry("printed", "13600.00").containsEntry("difference", "100.00");
    }

    @Test
    void aWrongGrandTotalIsReportedWithBothWaysTheTaxCouldBeTreated() {
        RuleResult result = run(Inputs.invoice().set("grandTotal", "17930.00"));

        assertThat(result.outcome()).isEqualTo(RuleOutcome.FLAG);
        assertThat(mismatches(result).get(0)).containsEntry("check", "GRAND_TOTAL").containsEntry("expected", "15930.00")
                .containsEntry("expectedIfTaxInLines", "13500.00").containsEntry("printed", "17930.00").containsEntry("difference", "2000.00");
    }

    @Test
    void aGrandTotalWithoutTheTaxAddedIsAcceptedBecauseTheTaxMayAlreadyBeInTheLines() {
        RuleResult result = run(Inputs.invoice().set("grandTotal", "13500.00"));

        assertThat(result.outcome()).isEqualTo(RuleOutcome.PASS);
        assertThat(result.values()).containsEntry("grandTotalFormula", "TAX_ALREADY_IN_LINES");
    }

    @Test
    void theRoundOffAllowanceIsInclusiveAtOneRupee() {
        assertThat(run(Inputs.invoice().set("grandTotal", "15930.40")).outcome()).isEqualTo(RuleOutcome.PASS);
        assertThat(run(Inputs.invoice().set("grandTotal", "15931.00")).outcome()).isEqualTo(RuleOutcome.PASS);
        assertThat(run(Inputs.invoice().set("grandTotal", "15931.01")).outcome()).isEqualTo(RuleOutcome.FLAG);
        assertThat(run(Inputs.invoice().set("grandTotal", "15929.00")).outcome()).isEqualTo(RuleOutcome.PASS);
        assertThat(run(Inputs.invoice().set("grandTotal", "15928.99")).outcome()).isEqualTo(RuleOutcome.FLAG);
    }

    @Test
    void aDiscountIsSubtractedWhateverSignItIsPrintedWith() {
        assertThat(run(Inputs.invoice().set("discountTotal", "500.00").set("grandTotal", "15430.00")).outcome()).isEqualTo(RuleOutcome.PASS);
        assertThat(run(Inputs.invoice().set("discountTotal", "-500.00").set("grandTotal", "15430.00")).outcome()).isEqualTo(RuleOutcome.PASS);
        assertThat(run(Inputs.invoice().set("discountTotal", "500.00")).outcome()).isEqualTo(RuleOutcome.FLAG);
    }

    @Test
    void anUnreadableDiscountMeansTheGrandTotalIsNotCheckedRatherThanGuessed() {
        RuleResult result = run(Inputs.invoice().unreadable("discountTotal").set("grandTotal", "99999.00"));

        assertThat(result.outcome()).isEqualTo(RuleOutcome.PASS);
        assertThat(result.values()).containsEntry("grandTotalChecked", false).containsEntry("checksRun", 3);
        assertThat((List<?>) result.values().get("notChecked")).anyMatch(s -> s.toString().contains("discount was unreadable"));
    }

    @Test
    void aDiscountThatIsPrintedAsAbsentCountsAsZero() {
        RuleResult result = run(Inputs.invoice().absent("discountTotal"));

        assertThat(result.outcome()).isEqualTo(RuleOutcome.PASS);
        assertThat(result.values()).containsEntry("grandTotalChecked", true);
    }

    @Test
    void anUnreadableTaxIsNotAssumedToBeZeroWhenTheTotalOnlyFitsWithTax() {
        RuleResult result = run(Inputs.invoice().unreadable("taxTotal"));

        assertThat(result.outcome()).isEqualTo(RuleOutcome.PASS);
        assertThat(result.values()).containsEntry("grandTotalChecked", false);
    }

    @Test
    void linesWithoutAReadableQuantityAreCountedAsNotCheckedAndTheTotalsAreStillChecked() {
        Inputs.Doc invoice = Inputs.invoice().clearLines().line("Front bumper replacement", null, "5000.00", "10000.00").line("Headlamp assembly left", null, "3500.00", "3500.00");

        RuleResult result = run(invoice);

        assertThat(result.outcome()).isEqualTo(RuleOutcome.PASS);
        assertThat(result.values()).containsEntry("linesChecked", 0).containsEntry("subtotalChecked", true).containsEntry("grandTotalChecked", true);
        assertThat((List<?>) result.values().get("notChecked")).anyMatch(s -> s.toString().contains("2 line(s)"));
    }

    @Test
    void whenNothingCanBeRecalculatedTheRuleIsIncompleteAndNeverPasses() {
        RuleResult result = run(Inputs.invoice().clearLines().unreadable("subtotal").unreadable("grandTotal"));

        assertThat(result.outcome()).isEqualTo(RuleOutcome.INCOMPLETE);
    }

    @Test
    void aDoubtfulReadingMakesTheFlagInformationNotMaterial() {
        RuleResult result = run(Inputs.invoice().set("grandTotal", "17930.00").issue(IssueCode.NO_SOURCE_QUOTE));

        assertThat(result.outcome()).isEqualTo(RuleOutcome.FLAG);
        assertThat(result.severity()).isEqualTo(RuleSeverity.INFO);
        assertThat(result.reliability()).isEqualTo(InputReliability.DOUBTFUL);
        assertThat(result.reliabilityReasons()).containsExactly("NO_SOURCE_QUOTE");
    }

    @Test
    void theExtractionsOwnArithmeticIssueIsTheVeryThingReportedSoItDoesNotMakeTheReadingDoubtful() {
        RuleResult result = run(Inputs.invoice().set("grandTotal", "17930.00").issue(IssueCode.GRAND_TOTAL_MISMATCH));

        assertThat(result.severity()).isEqualTo(RuleSeverity.REVIEW);
        assertThat(result.reliability()).isEqualTo(InputReliability.RELIABLE);
    }

    @Test
    void theNewestCurrentInvoiceIsTheOneChecked() {
        Inputs.Doc older = Inputs.invoice().set("grandTotal", "99999.00").received(Inputs.T0.minusSeconds(3600));
        Inputs.Doc newer = Inputs.invoice();

        RuleResult result = rule.evaluate(Inputs.inputs().with(older, newer).build(), RuleProperties.defaults());

        assertThat(result.outcome()).isEqualTo(RuleOutcome.PASS);
        assertThat(result.values()).containsEntry("invoiceDocumentId", newer.id.toString());
    }

    @Test
    void aReplacedInvoiceIsIgnored() {
        Inputs.Doc replaced = Inputs.invoice().set("grandTotal", "99999.00").replaced().received(Inputs.T0.plusSeconds(3600));

        RuleResult result = rule.evaluate(Inputs.inputs().with(replaced, Inputs.invoice()).build(), RuleProperties.defaults());

        assertThat(result.outcome()).isEqualTo(RuleOutcome.PASS);
    }

    @Test
    void theSameInputsGiveTheSameResult() {
        Inputs.Doc invoice = Inputs.invoice().set("grandTotal", "17930.00");
        RuleInputs inputs = Inputs.inputs().with(invoice).build();

        assertThat(rule.evaluate(inputs, RuleProperties.defaults())).isEqualTo(rule.evaluate(inputs, RuleProperties.defaults()));
    }

    @Test
    void aLooserRoundOffAllowanceIsHonoured() {
        RuleProperties loose = new RuleProperties(RuleProperties.defaults().timeZone(), new java.math.BigDecimal("5.00"), RuleProperties.defaults().lineBaseAllowance(),
                RuleProperties.defaults().variancePercentThreshold(), RuleProperties.defaults().varianceMinimumIncrease(), 0, 180, RuleProperties.defaults().nearDuplicateSimilarity(), 2, 365, 10);

        assertThat(rule.evaluate(Inputs.inputs().with(Inputs.invoice().set("grandTotal", "15934.00")).build(), loose).outcome()).isEqualTo(RuleOutcome.PASS);
        assertThat(rule.evaluate(Inputs.inputs().with(Inputs.invoice().set("grandTotal", "15934.00")).build(), RuleProperties.defaults()).outcome()).isEqualTo(RuleOutcome.FLAG);
    }
}
