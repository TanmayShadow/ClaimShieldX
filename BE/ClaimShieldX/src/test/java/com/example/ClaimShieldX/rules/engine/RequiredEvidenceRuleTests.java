package com.example.ClaimShieldX.rules.engine;

import com.example.ClaimShieldX.documents.model.DocumentType;
import com.example.ClaimShieldX.readiness.model.Readiness;
import com.example.ClaimShieldX.readiness.model.RequirementLevel;
import com.example.ClaimShieldX.rules.config.RuleProperties;
import com.example.ClaimShieldX.rules.engine.RuleInputs.ChecklistFacts;
import com.example.ClaimShieldX.rules.model.RuleOutcome;
import com.example.ClaimShieldX.rules.model.RuleResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RequiredEvidenceRuleTests {
    private final RequiredEvidenceRule rule = new RequiredEvidenceRule();

    private RuleResult run(ChecklistFacts checklist) {
        return rule.evaluate(Inputs.inputs().checklist(checklist).build(), RuleProperties.defaults());
    }

    @Test
    void aCompleteChecklistPasses() {
        assertThat(run(Inputs.checklist()).outcome()).isEqualTo(RuleOutcome.PASS);
    }

    @Test
    void aStageWithNoChecklistIsNotApplicable() {
        RuleResult result = run(Inputs.checklist(Readiness.READY_WITH_WARNINGS, List.of(), List.of(new ChecklistFacts.Issue("NO_CHECKLIST_FOR_STAGE", null, false, List.of()))));

        assertThat(result.outcome()).isEqualTo(RuleOutcome.NOT_APPLICABLE);
    }

    @Test
    void aMissingDocumentIsIncompleteWithTheChecksThatCannotRunNeverAFlag() {
        RuleResult result = run(Inputs.checklist(Readiness.NOT_READY, List.of(Inputs.entry(DocumentType.FINAL_INVOICE, RequirementLevel.REQUIRED)),
                List.of(new ChecklistFacts.Issue("DOCUMENT_MISSING", DocumentType.FINAL_INVOICE, true, List.of("INVOICE_ARITHMETIC", "REPEATED_LINE_ITEMS")))));

        assertThat(result.outcome()).isEqualTo(RuleOutcome.INCOMPLETE);
        assertThat(result.severity()).isNull();
        assertThat(result.values().get("missing")).isEqualTo(List.of("FINAL_INVOICE:DOCUMENT_MISSING"));
        assertThat(result.values().get("checksAffected")).isEqualTo(List.of("INVOICE_ARITHMETIC", "REPEATED_LINE_ITEMS"));
        assertThat(result.explanation()).contains("about the evidence, not about the claim");
        assertThat(result.evidence()).singleElement().satisfies(e -> assertThat(e.documentType()).isEqualTo("FINAL_INVOICE"));
    }

    @Test
    void severalGapsAreAllListedAndTheAffectedChecksAreNotDuplicated() {
        RuleResult result = run(Inputs.checklist(Readiness.READY_WITH_WARNINGS, List.of(Inputs.entry(DocumentType.GARAGE_ESTIMATE, RequirementLevel.REQUIRED), Inputs.entry(DocumentType.FINAL_INVOICE, RequirementLevel.REQUIRED)),
                List.of(new ChecklistFacts.Issue("DOCUMENT_MISSING", DocumentType.GARAGE_ESTIMATE, false, List.of("ESTIMATE_INVOICE_VARIANCE")),
                        new ChecklistFacts.Issue("DOCUMENT_NEEDS_REVIEW", DocumentType.FINAL_INVOICE, false, List.of("ESTIMATE_INVOICE_VARIANCE")))));

        assertThat(result.outcome()).isEqualTo(RuleOutcome.INCOMPLETE);
        assertThat((List<?>) result.values().get("issues")).hasSize(2);
        assertThat(result.values().get("checksAffected")).isEqualTo(List.of("ESTIMATE_INVOICE_VARIANCE"));
    }
}
