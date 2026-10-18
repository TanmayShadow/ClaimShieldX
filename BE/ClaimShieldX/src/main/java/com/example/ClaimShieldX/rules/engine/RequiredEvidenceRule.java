package com.example.ClaimShieldX.rules.engine;

import com.example.ClaimShieldX.rules.config.RuleProperties;
import com.example.ClaimShieldX.rules.engine.RuleInputs.ChecklistFacts;
import com.example.ClaimShieldX.rules.model.EvidenceRef;
import com.example.ClaimShieldX.rules.model.InputReliability;
import com.example.ClaimShieldX.rules.model.RuleDefinition;
import com.example.ClaimShieldX.rules.model.RuleResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Turns the document checklist of B4 into a rule result, so what could not be checked is recorded with the run instead of
 * living in a separate report. A gap is INCOMPLETE, never FLAG: missing evidence is a completeness matter, not a finding.
 */
public final class RequiredEvidenceRule implements Rule {
    public static final RuleDefinition DEFINITION = new RuleDefinition("REQUIRED_EVIDENCE", "1", "Required evidence",
            "Lists the documents the checklist expects at this claim's stage that are missing, failed or still need review, and the checks that cannot fully run because of them.",
            List.of("Claim stage and incident type", "Document checklist and the claim's documents"),
            List.of("A document may simply not have arrived yet.", "A late or missing document says nothing about the claim itself."));

    @Override
    public RuleDefinition definition() {
        return DEFINITION;
    }

    @Override
    public RuleResult evaluate(RuleInputs inputs, RuleProperties config) {
        ChecklistFacts checklist = inputs.checklist();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("readiness", checklist.readiness().name());
        if (checklist.entries().isEmpty()) {
            return Results.notApplicable(DEFINITION, "No document checklist is defined for this claim's stage, so no evidence requirement was checked.", values);
        }
        if (checklist.issues().isEmpty()) {
            return Results.pass(DEFINITION, InputReliability.RELIABLE, List.of(),
                    "Every document the checklist asks for has arrived, none failed processing and none is waiting for review.", values, List.of());
        }

        List<String> missing = new ArrayList<>();
        List<Map<String, Object>> issues = new ArrayList<>();
        List<EvidenceRef> evidence = new ArrayList<>();
        TreeSet<String> affected = new TreeSet<>();
        for (ChecklistFacts.Issue issue : checklist.issues()) {
            String type = issue.documentType() == null ? "STAGE" : issue.documentType().name();
            missing.add(type + ":" + issue.code());
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("documentType", type);
            entry.put("code", issue.code());
            entry.put("blocksStage", issue.blocker());
            entry.put("checksAffected", issue.blocks());
            issues.add(entry);
            affected.addAll(issue.blocks());
            evidence.add(EvidenceRef.checklistItem(type, issue.code()));
        }
        values.put("issues", issues);
        values.put("checksAffected", List.copyOf(affected));
        String consequence = affected.isEmpty() ? "" : " Checks that cannot fully run: " + String.join(", ", affected) + ".";
        return Results.incomplete(DEFINITION, issues.size() + " expected document" + (issues.size() == 1 ? " is" : "s are")
                + " missing, failed or still need review." + consequence + " This is about the evidence, not about the claim.", missing, values, evidence);
    }
}
