package com.example.ClaimShieldX.rules.engine;

import com.example.ClaimShieldX.documents.model.DocumentType;
import com.example.ClaimShieldX.rules.config.RuleProperties;
import com.example.ClaimShieldX.rules.model.InputReliability;
import com.example.ClaimShieldX.rules.model.RuleOutcome;
import com.example.ClaimShieldX.rules.model.RuleResult;
import com.example.ClaimShieldX.rules.model.RuleSeverity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentFileDuplicateRuleTests {
    private final DocumentFileDuplicateRule rule = new DocumentFileDuplicateRule();

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> matches(RuleResult result) {
        return (List<Map<String, Object>>) result.values().get("matches");
    }

    @Test
    void documentsWithNoIdenticalCopyPass() {
        RuleResult result = rule.evaluate(Inputs.inputs().with(Inputs.invoice(), Inputs.estimate()).build(), RuleProperties.defaults());

        assertThat(result.outcome()).isEqualTo(RuleOutcome.PASS);
        assertThat(result.values()).containsEntry("documentsChecked", 2).containsEntry("detects", "byte-identical files only");
        assertThat(result.reliability()).isEqualTo(InputReliability.RELIABLE);
    }

    @Test
    void aClaimWithNoDocumentsIsIncompleteNotAPass() {
        assertThat(rule.evaluate(Inputs.inputs().build(), RuleProperties.defaults()).outcome()).isEqualTo(RuleOutcome.INCOMPLETE);
    }

    @Test
    void theSameInvoiceFileOnAnotherClaimIsMaterial() {
        Inputs.Doc invoice = Inputs.invoice();
        RuleResult result = rule.evaluate(Inputs.inputs().with(invoice).match(Inputs.fileMatch(invoice, Inputs.OTHER_CLAIM, DocumentType.FINAL_INVOICE, false)).build(), RuleProperties.defaults());

        assertThat(result.outcome()).isEqualTo(RuleOutcome.FLAG);
        assertThat(result.severity()).isEqualTo(RuleSeverity.REVIEW);
        assertThat(matches(result).get(0)).containsEntry("relation", "SAME_FILE_ON_ANOTHER_CLAIM").containsEntry("otherClaimInternalId", "CLM-2");
        assertThat(result.evidence()).hasSize(2);
        assertThat(result.considerations()).isNotEmpty();
    }

    @Test
    void anEarlierVersionThatWasReplacedOnThisClaimIsInformationOnly() {
        Inputs.Doc invoice = Inputs.invoice();
        RuleResult result = rule.evaluate(Inputs.inputs().with(invoice).match(Inputs.fileMatch(invoice, Inputs.CLAIM_ID, DocumentType.FINAL_INVOICE, true)).build(), RuleProperties.defaults());

        assertThat(result.outcome()).isEqualTo(RuleOutcome.FLAG);
        assertThat(result.severity()).isEqualTo(RuleSeverity.INFO);
        assertThat(matches(result).get(0)).containsEntry("relation", "EARLIER_VERSION_ON_THIS_CLAIM");
    }

    @Test
    void theSameFileFiledUnderAnotherTypeOnTheSameClaimIsMaterial() {
        Inputs.Doc invoice = Inputs.invoice();
        RuleResult result = rule.evaluate(Inputs.inputs().with(invoice).match(Inputs.fileMatch(invoice, Inputs.CLAIM_ID, DocumentType.GARAGE_ESTIMATE, false)).build(), RuleProperties.defaults());

        assertThat(result.severity()).isEqualTo(RuleSeverity.REVIEW);
        assertThat(matches(result).get(0)).containsEntry("relation", "SAME_FILE_UNDER_ANOTHER_TYPE_ON_THIS_CLAIM");
    }

    @Test
    void aPolicyRecordSharedWithAnotherClaimIsExpectedAndNotMaterial() {
        Inputs.Doc policy = Inputs.plain(DocumentType.POLICY_RECORD);
        RuleResult result = rule.evaluate(Inputs.inputs().with(policy).match(Inputs.fileMatch(policy, Inputs.OTHER_CLAIM, DocumentType.POLICY_RECORD, false)).build(), RuleProperties.defaults());

        assertThat(result.outcome()).isEqualTo(RuleOutcome.FLAG);
        assertThat(result.severity()).isEqualTo(RuleSeverity.INFO);
        assertThat(matches(result).get(0)).containsEntry("relation", "REFERENCE_DOCUMENT_SHARED_WITH_ANOTHER_CLAIM");
    }

    @Test
    void aPolicyRecordFileThatTurnsUpAsADamagePhotographElsewhereIsNotTreatedAsASharedReference() {
        Inputs.Doc policy = Inputs.plain(DocumentType.POLICY_RECORD);
        RuleResult result = rule.evaluate(Inputs.inputs().with(policy).match(Inputs.fileMatch(policy, Inputs.OTHER_CLAIM, DocumentType.DAMAGE_PHOTOGRAPH, false)).build(), RuleProperties.defaults());

        assertThat(result.severity()).isEqualTo(RuleSeverity.REVIEW);
    }

    @Test
    void aDamagePhotographReusedOnAnotherClaimIsMaterial() {
        Inputs.Doc photo = Inputs.plain(DocumentType.DAMAGE_PHOTOGRAPH);
        RuleResult result = rule.evaluate(Inputs.inputs().with(photo).match(Inputs.fileMatch(photo, Inputs.OTHER_CLAIM, DocumentType.DAMAGE_PHOTOGRAPH, false)).build(), RuleProperties.defaults());

        assertThat(result.severity()).isEqualTo(RuleSeverity.REVIEW);
    }

    @Test
    void oneMaterialMatchMakesTheWholeResultMaterialEvenIfOthersAreExpected() {
        Inputs.Doc policy = Inputs.plain(DocumentType.POLICY_RECORD);
        Inputs.Doc invoice = Inputs.invoice();
        RuleResult result = rule.evaluate(Inputs.inputs().with(policy, invoice)
                .match(Inputs.fileMatch(policy, Inputs.OTHER_CLAIM, DocumentType.POLICY_RECORD, false))
                .match(Inputs.fileMatch(invoice, Inputs.OTHER_CLAIM, DocumentType.FINAL_INVOICE, false)).build(), RuleProperties.defaults());

        assertThat(result.severity()).isEqualTo(RuleSeverity.REVIEW);
        assertThat(matches(result)).hasSize(2);
        assertThat((Map<?, ?>) result.values().get("matchesByRelation")).hasSize(2);
    }

    @Test
    void aReplacedDocumentOfThisClaimIsNotItselfChecked() {
        Inputs.Doc old = Inputs.invoice().replaced();
        RuleResult result = rule.evaluate(Inputs.inputs().with(old, Inputs.invoice()).match(Inputs.fileMatch(old, Inputs.OTHER_CLAIM, DocumentType.FINAL_INVOICE, false)).build(), RuleProperties.defaults());

        assertThat(result.outcome()).isEqualTo(RuleOutcome.PASS);
        assertThat(result.values()).containsEntry("documentsChecked", 1);
    }

    @Test
    void theOrderOfTheMatchesDoesNotDependOnTheOrderTheyWereSupplied() {
        Inputs.Doc invoice = Inputs.invoice();
        var first = Inputs.fileMatch(invoice, Inputs.OTHER_CLAIM, DocumentType.FINAL_INVOICE, false);
        var second = Inputs.fileMatch(invoice, Inputs.OTHER_CLAIM, DocumentType.GARAGE_ESTIMATE, false);

        RuleResult a = rule.evaluate(Inputs.inputs().with(invoice).match(first).match(second).build(), RuleProperties.defaults());
        RuleResult b = rule.evaluate(Inputs.inputs().with(invoice).match(second).match(first).build(), RuleProperties.defaults());

        assertThat(a).isEqualTo(b);
    }
}
