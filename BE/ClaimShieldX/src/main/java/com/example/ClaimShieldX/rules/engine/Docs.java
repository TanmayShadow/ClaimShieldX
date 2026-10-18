package com.example.ClaimShieldX.rules.engine;

import com.example.ClaimShieldX.documents.model.DocumentType;
import com.example.ClaimShieldX.readiness.model.RequirementLevel;
import com.example.ClaimShieldX.rules.engine.RuleInputs.ChecklistFacts;
import com.example.ClaimShieldX.rules.engine.RuleInputs.DocumentFacts;

import java.util.Comparator;
import java.util.List;

/** Which of a claim's documents a rule looks at, chosen the same way the checklist chooses them: newest current one wins. */
final class Docs {
    private static final Comparator<DocumentFacts> NEWEST_FIRST = Comparator.comparing(DocumentFacts::receivedAt).thenComparing(DocumentFacts::id).reversed();

    private Docs() {
    }

    static List<DocumentFacts> current(RuleInputs inputs, DocumentType type) {
        return inputs.documents().stream().filter(d -> !d.superseded() && d.type() == type).sorted(NEWEST_FIRST).toList();
    }

    static DocumentFacts newest(RuleInputs inputs, DocumentType type) {
        return current(inputs, type).stream().findFirst().orElse(null);
    }

    /** True when the stage and incident type ask for this document at all (required or optional). */
    static boolean expected(RuleInputs inputs, DocumentType type) {
        return inputs.checklist().entries().stream().anyMatch(e -> e.documentType() == type && e.requirement() != RequirementLevel.NOT_APPLICABLE);
    }

    static boolean required(RuleInputs inputs, DocumentType type) {
        return inputs.checklist().entries().stream().anyMatch(e -> e.documentType() == type && e.requirement() == RequirementLevel.REQUIRED);
    }

    /** Nothing was received and the checklist does not ask for it: the rule does not apply, it is not "missing evidence". */
    static boolean notApplicable(RuleInputs inputs, DocumentType type) {
        return newest(inputs, type) == null && !expected(inputs, type);
    }

    static ChecklistFacts checklist(RuleInputs inputs) {
        return inputs.checklist();
    }
}
