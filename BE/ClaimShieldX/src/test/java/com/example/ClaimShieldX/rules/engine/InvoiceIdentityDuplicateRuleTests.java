package com.example.ClaimShieldX.rules.engine;

import com.example.ClaimShieldX.extraction.schema.IssueCode;
import com.example.ClaimShieldX.rules.config.RuleProperties;
import com.example.ClaimShieldX.rules.engine.RuleInputs.InvoiceIdentity;
import com.example.ClaimShieldX.rules.model.RuleOutcome;
import com.example.ClaimShieldX.rules.model.RuleResult;
import com.example.ClaimShieldX.rules.model.RuleSeverity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceIdentityDuplicateRuleTests {
    private final InvoiceIdentityDuplicateRule rule = new InvoiceIdentityDuplicateRule();

    private RuleResult run(Inputs.Doc invoice, InvoiceIdentity... others) {
        Inputs.Builder b = Inputs.inputs().with(invoice);
        for (InvoiceIdentity o : others) {
            b.candidate(o);
        }
        return rule.evaluate(b.build(), RuleProperties.defaults());
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> matches(RuleResult result) {
        return (List<Map<String, Object>>) result.values().get("matches");
    }

    @Test
    void anInvoiceWithNoOtherInvoiceOfTheSameNumberPasses() {
        RuleResult result = run(Inputs.invoice());

        assertThat(result.outcome()).isEqualTo(RuleOutcome.PASS);
        assertThat(result.values()).containsEntry("invoiceNumber", "INV-2026-0042").containsEntry("issuer", "GAR-1");
    }

    @Test
    void sameNumberIssuerAmountAndDateOnAnotherClaimIsMaterial() {
        RuleResult result = run(Inputs.invoice(), Inputs.identity(Inputs.OTHER_CLAIM, "INV-2026-0042", "GAR-1", "15930.00", "2026-09-15"));

        assertThat(result.outcome()).isEqualTo(RuleOutcome.FLAG);
        assertThat(result.severity()).isEqualTo(RuleSeverity.REVIEW);
        assertThat(matches(result).get(0)).containsEntry("level", "SAME_NUMBER_ISSUER_AMOUNT_AND_DATE").containsEntry("location", "ANOTHER_CLAIM").containsEntry("amountDifference", "0.00");
        assertThat(result.explanation()).contains("not proof");
    }

    @Test
    void sameNumberIssuerAndAmountButAnotherDateIsStillMaterial() {
        RuleResult result = run(Inputs.invoice(), Inputs.identity(Inputs.OTHER_CLAIM, "INV-2026-0042", "GAR-1", "15930.00", "2026-08-01"));

        assertThat(matches(result).get(0)).containsEntry("level", "SAME_NUMBER_ISSUER_AND_AMOUNT");
        assertThat(result.severity()).isEqualTo(RuleSeverity.REVIEW);
    }

    @Test
    void aDifferentAmountLooksLikeACorrectedInvoiceSoItIsInformationOnly() {
        RuleResult result = run(Inputs.invoice(), Inputs.identity(Inputs.OTHER_CLAIM, "INV-2026-0042", "GAR-1", "14000.00", "2026-09-15"));

        assertThat(result.outcome()).isEqualTo(RuleOutcome.FLAG);
        assertThat(result.severity()).isEqualTo(RuleSeverity.INFO);
        assertThat(matches(result).get(0)).containsEntry("level", "SAME_NUMBER_ISSUER_DIFFERENT_AMOUNT").containsEntry("amountDifference", "1930.00");
        assertThat(result.explanation()).contains("possibly a corrected invoice");
    }

    @Test
    void theSameNumberFromAnotherIssuerIsCountedButNotMatched() {
        RuleResult result = run(Inputs.invoice(), Inputs.identity(Inputs.OTHER_CLAIM, "INV-2026-0042", "GAR-9", "15930.00", "2026-09-15"));

        assertThat(result.outcome()).isEqualTo(RuleOutcome.PASS);
        assertThat(result.values()).containsEntry("sameNumberFromOtherIssuers", 1);
    }

    @Test
    void separatorsAndCaseDoNotHideTheSameInvoiceNumber() {
        RuleResult result = run(Inputs.invoice(), Inputs.identity(Inputs.OTHER_CLAIM, "inv 2026/0042", "GAR-1", "15930.00", "2026-09-15"));

        assertThat(result.outcome()).isEqualTo(RuleOutcome.FLAG);
    }

    @Test
    void leadingZerosAreKeptSoAnotherNumberIsNotMatched() {
        RuleResult result = run(Inputs.invoice(), Inputs.identity(Inputs.OTHER_CLAIM, "INV-2026-42", "GAR-1", "15930.00", "2026-09-15"));

        assertThat(result.outcome()).isEqualTo(RuleOutcome.PASS);
    }

    @Test
    void aReplacedEarlierVersionOnThisSameClaimIsTheExpectedReissue() {
        InvoiceIdentity earlier = new InvoiceIdentity(Inputs.CLAIM_ID, "CLM-1", UUID.randomUUID(), UUID.randomUUID(), com.example.ClaimShieldX.documents.model.DocumentStatus.VERIFIED,
                true, null, Inputs.T0.minusSeconds(3600), "INV-2026-0042", "GAR-1", "GAR-1", new java.math.BigDecimal("15930.00"), "INR", "2026-09-15", false, false);

        RuleResult result = run(Inputs.invoice(), earlier);

        assertThat(result.outcome()).isEqualTo(RuleOutcome.FLAG);
        assertThat(result.severity()).isEqualTo(RuleSeverity.INFO);
        assertThat(matches(result).get(0)).containsEntry("location", "EARLIER_VERSION_ON_THIS_CLAIM");
        assertThat(result.explanation()).contains("expected result of a reissue");
    }

    @Test
    void anotherCurrentInvoiceWithTheSameNumberOnThisClaimIsMaterialWhenTheAmountIsTheSame() {
        InvoiceIdentity other = Inputs.identity(Inputs.CLAIM_ID, "INV-2026-0042", "GAR-1", "15930.00", "2026-09-15");

        RuleResult result = run(Inputs.invoice(), other);

        assertThat(matches(result).get(0)).containsEntry("location", "ANOTHER_INVOICE_ON_THIS_CLAIM");
        assertThat(result.severity()).isEqualTo(RuleSeverity.REVIEW);
    }

    @Test
    void theInvoiceItselfIsNeverMatchedWithItself() {
        Inputs.Doc invoice = Inputs.invoice();
        InvoiceIdentity self = new InvoiceIdentity(Inputs.CLAIM_ID, "CLM-1", invoice.id, invoice.extractionId, com.example.ClaimShieldX.documents.model.DocumentStatus.VERIFIED,
                false, null, Inputs.T0, "INV-2026-0042", "GAR-1", "GAR-1", new java.math.BigDecimal("15930.00"), "INR", "2026-09-15", false, false);

        assertThat(run(invoice, self).outcome()).isEqualTo(RuleOutcome.PASS);
    }

    @Test
    void aCandidateThatIsNotReallyTheSameNumberIsIgnoredEvenIfTheLoaderSuppliedIt() {
        assertThat(run(Inputs.invoice(), Inputs.identity(Inputs.OTHER_CLAIM, "INV-9999", "GAR-1", "15930.00", "2026-09-15")).outcome()).isEqualTo(RuleOutcome.PASS);
    }

    @Test
    void anUnreadableInvoiceNumberCannotBeCompared() {
        assertThat(run(Inputs.invoice().unreadable("documentNumber")).outcome()).isEqualTo(RuleOutcome.INCOMPLETE);
        assertThat(run(Inputs.invoice().set("documentNumber", "---")).outcome()).isEqualTo(RuleOutcome.INCOMPLETE);
    }

    @Test
    void withoutAnyIssuerAtAllTheInvoiceCannotBeCompared() {
        RuleInputs inputs = Inputs.inputs().claim(Inputs.claimB().garage(null).build()).with(Inputs.invoice().absent("garageIdentifier")).build();

        RuleResult result = rule.evaluate(inputs, RuleProperties.defaults());

        assertThat(result.outcome()).isEqualTo(RuleOutcome.INCOMPLETE);
        assertThat(result.values().get("missing")).isEqualTo(List.of("INVOICE_ISSUER"));
    }

    @Test
    void anInvoiceThatPrintsNoGarageIdentifierUsesTheGarageRecordedOnTheClaim() {
        RuleResult result = run(Inputs.invoice().absent("garageIdentifier"), Inputs.identity(Inputs.OTHER_CLAIM, "INV-2026-0042", null, "GAR-1", "15930.00", "2026-09-15"));

        assertThat(result.outcome()).isEqualTo(RuleOutcome.FLAG);
        assertThat(result.severity()).isEqualTo(RuleSeverity.REVIEW);
        assertThat(matches(result).get(0)).containsEntry("issuerBasis", "CLAIM_RECORD_GARAGE");
        assertThat(result.values()).containsEntry("claimGarage", "GAR-1");
    }

    @Test
    void differentGaragesOnTheClaimsMeanDifferentIssuersWhenNeitherInvoicePrintsOne() {
        RuleResult result = run(Inputs.invoice().absent("garageIdentifier"), Inputs.identity(Inputs.OTHER_CLAIM, "INV-2026-0042", null, "GAR-OTHER", "15930.00", "2026-09-15"));

        assertThat(result.outcome()).isEqualTo(RuleOutcome.PASS);
        assertThat(result.values()).containsEntry("sameNumberFromOtherIssuers", 1);
    }

    @Test
    void identifiersPrintedOnBothInvoicesDecideEvenIfTheClaimsRecordTheSameGarage() {
        RuleResult result = run(Inputs.invoice().set("garageIdentifier", "GST-AAA"), Inputs.identity(Inputs.OTHER_CLAIM, "INV-2026-0042", "GST-BBB", "GAR-1", "15930.00", "2026-09-15"));

        assertThat(result.outcome()).isEqualTo(RuleOutcome.PASS);
    }

    @Test
    void printedIdentifiersThatAgreeAreTheBasisEvenIfTheClaimGaragesDiffer() {
        RuleResult result = run(Inputs.invoice().set("garageIdentifier", "GST-AAA"), Inputs.identity(Inputs.OTHER_CLAIM, "INV-2026-0042", "GST-AAA", "GAR-OTHER", "15930.00", "2026-09-15"));

        assertThat(result.outcome()).isEqualTo(RuleOutcome.FLAG);
        assertThat(matches(result).get(0)).containsEntry("issuerBasis", "DOCUMENT_IDENTIFIER");
    }

    @Test
    void anIdentifierOnOnlyOneOfTheInvoicesFallsBackToTheGaragesRecordedOnTheClaims() {
        RuleResult result = run(Inputs.invoice().set("garageIdentifier", "GST-AAA"), Inputs.identity(Inputs.OTHER_CLAIM, "INV-2026-0042", null, "GAR-1", "15930.00", "2026-09-15"));

        assertThat(matches(result).get(0)).containsEntry("issuerBasis", "CLAIM_RECORD_GARAGE");
    }

    @Test
    void anUnknownAmountOnTheInvoiceStillAllowsTheNumberAndIssuerComparisonButNeverCountsAsTheSameAmount() {
        RuleResult result = run(Inputs.invoice().unreadable("grandTotal"), Inputs.identity(Inputs.OTHER_CLAIM, "INV-2026-0042", "GAR-1", "15930.00", "2026-09-15"));

        assertThat(matches(result).get(0)).containsEntry("level", "SAME_NUMBER_ISSUER_DIFFERENT_AMOUNT");
        assertThat(matches(result).get(0).get("amountDifference")).isNull();
        assertThat(result.severity()).isEqualTo(RuleSeverity.INFO);
    }

    @Test
    void aDoubtfullyReadInvoiceMakesTheMatchNotMaterial() {
        RuleResult result = run(Inputs.invoice().issue(IssueCode.MODEL_REPORTED_PROBLEM), Inputs.identity(Inputs.OTHER_CLAIM, "INV-2026-0042", "GAR-1", "15930.00", "2026-09-15"));

        assertThat(result.outcome()).isEqualTo(RuleOutcome.FLAG);
        assertThat(result.severity()).isEqualTo(RuleSeverity.INFO);
    }

    @Test
    void noInvoiceOrAnUnreadInvoiceIsIncompleteAndAnUnexpectedOneIsNotApplicable() {
        assertThat(rule.evaluate(Inputs.inputs().build(), RuleProperties.defaults()).outcome()).isEqualTo(RuleOutcome.INCOMPLETE);
        assertThat(run(Inputs.invoice().notExtracted()).outcome()).isEqualTo(RuleOutcome.INCOMPLETE);
        assertThat(rule.evaluate(Inputs.inputs().checklist(Inputs.checklist(com.example.ClaimShieldX.readiness.model.Readiness.READY,
                List.of(Inputs.entry(com.example.ClaimShieldX.documents.model.DocumentType.FINAL_INVOICE, com.example.ClaimShieldX.readiness.model.RequirementLevel.NOT_APPLICABLE)), List.of())).build(),
                RuleProperties.defaults()).outcome()).isEqualTo(RuleOutcome.NOT_APPLICABLE);
    }

    @Test
    void invoiceKeysAreAsciiLettersAndDigitsOnly() {
        assertThat(InvoiceKey.of("INV/2026/0042")).isEqualTo("inv20260042");
        assertThat(InvoiceKey.of("  inv 2026 0042 ")).isEqualTo("inv20260042");
        assertThat(InvoiceKey.of("---")).isNull();
        assertThat(InvoiceKey.of("")).isNull();
        assertThat(InvoiceKey.of(null)).isNull();
        assertThat(InvoiceKey.of("चालान")).isNull();
    }
}
