package com.example.ClaimShieldX.readiness.service;

import com.example.ClaimShieldX.claims.model.ClaimStage;
import com.example.ClaimShieldX.claims.model.IncidentType;
import com.example.ClaimShieldX.documents.model.DocumentStatus;
import com.example.ClaimShieldX.documents.model.DocumentType;
import com.example.ClaimShieldX.readiness.dto.ChecklistItem;
import com.example.ClaimShieldX.readiness.dto.ReadinessIssue;
import com.example.ClaimShieldX.readiness.dto.ReadinessIssue.Code;
import com.example.ClaimShieldX.readiness.dto.ReadinessSummary;
import com.example.ClaimShieldX.readiness.model.EvidenceState;
import com.example.ClaimShieldX.readiness.model.InvestigationCheck;
import com.example.ClaimShieldX.readiness.model.Readiness;
import com.example.ClaimShieldX.readiness.model.RequirementLevel;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Turns the checklist configuration and a claim's documents into a checklist and a readiness level. Pure and
 * deterministic: no database, no clock, so every rule here is unit-tested directly.
 *
 * <p>The outcome is about evidence, never about the claim. A missing document never becomes a negative finding;
 * it is reported as "these checks cannot run".
 */
public final class ChecklistEvaluator {
    /** The configuration value for "every incident type". */
    public static final String ANY_INCIDENT_TYPE = "ANY";

    private ChecklistEvaluator() {
    }

    public record Rule(ClaimStage stage, String incidentType, DocumentType documentType, RequirementLevel requirement, boolean gating) {
    }

    /** A current (non-superseded) document of the claim. */
    public record Evidence(UUID id, DocumentType documentType, DocumentStatus status, Instant receivedAt) {
    }

    public record Evaluation(List<ChecklistItem> items, List<ReadinessIssue> blockers, List<ReadinessIssue> warnings, Readiness readiness) {
        public ReadinessSummary summary() {
            return new ReadinessSummary(readiness, blockers.size(), warnings.size());
        }
    }

    public static Evaluation evaluate(ClaimStage stage, IncidentType incidentType, Collection<Rule> rules, Collection<Evidence> documents) {
        Map<DocumentType, Rule> effective = effectiveRules(stage, incidentType, rules);
        if (effective.isEmpty()) {
            return noChecklist(stage);
        }

        Map<DocumentType, List<Evidence>> byType = documents.stream().collect(Collectors.groupingBy(Evidence::documentType));
        Comparator<Evidence> newestFirst = Comparator.comparing(Evidence::receivedAt).thenComparing(Evidence::id).reversed();

        Map<DocumentType, EvidenceState> states = new EnumMap<>(DocumentType.class);
        Map<DocumentType, List<UUID>> ids = new EnumMap<>(DocumentType.class);
        for (DocumentType type : effective.keySet()) {
            List<Evidence> current = byType.getOrDefault(type, List.of()).stream().sorted(newestFirst).toList();
            states.put(type, current.isEmpty() ? EvidenceState.NOT_RECEIVED : EvidenceState.of(current.get(0).status()));
            ids.put(type, current.stream().map(Evidence::id).toList());
        }

        Map<DocumentType, List<InvestigationCheck>> blocks = blockedChecks(effective, states);

        List<ChecklistItem> items = new ArrayList<>();
        List<ReadinessIssue> blockers = new ArrayList<>();
        List<ReadinessIssue> warnings = new ArrayList<>();
        for (Rule rule : effective.values()) {
            DocumentType type = rule.documentType();
            List<InvestigationCheck> blockedHere = blocks.getOrDefault(type, List.of());
            items.add(new ChecklistItem(type, rule.requirement(), rule.gating(), states.get(type), ids.get(type), blockedHere));

            Code code = issueCode(rule.requirement(), states.get(type), !blockedHere.isEmpty());
            if (code != null) {
                ReadinessIssue issue = new ReadinessIssue(code, type, rule.requirement(), message(code, type, blockedHere), blockedHere);
                boolean definesTheStage = rule.gating() && rule.requirement() == RequirementLevel.REQUIRED
                        && (code == Code.DOCUMENT_MISSING || code == Code.DOCUMENT_FAILED);
                (definesTheStage ? blockers : warnings).add(issue);
            }
        }
        return new Evaluation(List.copyOf(items), List.copyOf(blockers), List.copyOf(warnings), level(blockers, warnings));
    }

    /** A specific incident type replaces the "ANY" row for the same document type. */
    private static Map<DocumentType, Rule> effectiveRules(ClaimStage stage, IncidentType incidentType, Collection<Rule> rules) {
        Map<DocumentType, Rule> effective = new EnumMap<>(DocumentType.class);
        if (stage == null) {
            return effective;
        }
        String specific = incidentType == null ? null : incidentType.name();
        for (Rule rule : rules) {
            if (rule.stage() == stage && ANY_INCIDENT_TYPE.equals(rule.incidentType())) {
                effective.put(rule.documentType(), rule);
            }
        }
        for (Rule rule : rules) {
            if (rule.stage() == stage && rule.incidentType().equals(specific)) {
                effective.put(rule.documentType(), rule);
            }
        }
        return effective;
    }

    /**
     * A check is relevant at this stage only if it needs documents the stage asks for at all; the variance check, for
     * example, does not exist before a final invoice is expected. A relevant check cannot run while any document it
     * needs is missing or failed, and that document's entry lists the check.
     */
    private static Map<DocumentType, List<InvestigationCheck>> blockedChecks(Map<DocumentType, Rule> effective, Map<DocumentType, EvidenceState> states) {
        Map<DocumentType, List<InvestigationCheck>> blocks = new EnumMap<>(DocumentType.class);
        for (InvestigationCheck check : InvestigationCheck.values()) {
            boolean relevant = check.documents().stream().allMatch(type -> {
                Rule rule = effective.get(type);
                return rule != null && rule.requirement() != RequirementLevel.NOT_APPLICABLE;
            });
            if (!relevant) {
                continue;
            }
            for (DocumentType type : check.documents()) {
                if (states.get(type).isUnusable()) {
                    blocks.computeIfAbsent(type, k -> new ArrayList<>()).add(check);
                }
            }
        }
        return blocks;
    }

    /** Null when the entry needs no attention. */
    private static Code issueCode(RequirementLevel requirement, EvidenceState state, boolean blocksAnyCheck) {
        if (requirement == RequirementLevel.NOT_APPLICABLE) {
            return null;
        }
        boolean required = requirement == RequirementLevel.REQUIRED;
        return switch (state) {
            case NOT_RECEIVED -> required || blocksAnyCheck ? Code.DOCUMENT_MISSING : null;
            case FAILED -> Code.DOCUMENT_FAILED;
            case NEEDS_REVIEW -> Code.DOCUMENT_NEEDS_REVIEW;
            case PROCESSING -> required ? Code.DOCUMENT_PROCESSING : null;
            case RECEIVED, VERIFIED -> null;
        };
    }

    private static Readiness level(List<ReadinessIssue> blockers, List<ReadinessIssue> warnings) {
        if (!blockers.isEmpty()) {
            return Readiness.NOT_READY;
        }
        return warnings.isEmpty() ? Readiness.READY : Readiness.READY_WITH_WARNINGS;
    }

    private static Evaluation noChecklist(ClaimStage stage) {
        String where = stage == null ? "this claim stage" : "stage " + stage;
        ReadinessIssue issue = new ReadinessIssue(Code.NO_CHECKLIST_FOR_STAGE, null, null,
                "No document checklist is defined for " + where + ", so no evidence requirement was checked.", null);
        return new Evaluation(List.of(), List.of(), List.of(issue), Readiness.READY_WITH_WARNINGS);
    }

    private static String message(Code code, DocumentType type, List<InvestigationCheck> blocked) {
        String subject = label(type);
        String what = switch (code) {
            case DOCUMENT_MISSING -> subject + " has not been received.";
            case DOCUMENT_FAILED -> subject + " could not be processed. Upload a clearer copy or review it by hand.";
            case DOCUMENT_NEEDS_REVIEW -> subject + " needs human review before its content is relied on.";
            case DOCUMENT_PROCESSING -> subject + " is still being processed.";
            case NO_CHECKLIST_FOR_STAGE -> throw new IllegalArgumentException("not a document issue");
        };
        if (blocked.isEmpty()) {
            return what;
        }
        String checks = blocked.stream().map(InvestigationCheck::label).collect(Collectors.joining(", "));
        return what + " Checks that cannot run without it: " + checks + ".";
    }

    // An exhaustive switch, so adding a document type fails the build until it has a label.
    public static String label(DocumentType type) {
        return switch (type) {
            case CLAIM_FORM -> "Claim form";
            case POLICY_RECORD -> "Policy record";
            case VEHICLE_REGISTRATION -> "Vehicle registration (RC)";
            case DRIVING_LICENCE -> "Driving licence";
            case DAMAGE_PHOTOGRAPH -> "Damage photographs";
            case GARAGE_ESTIMATE -> "Garage estimate";
            case JOB_CARD -> "Job card";
            case SURVEYOR_REPORT -> "Surveyor report";
            case FINAL_INVOICE -> "Final invoice";
            case SUPPLEMENTARY_ESTIMATE -> "Supplementary estimate";
            case PAYMENT_PROOF -> "Payment proof";
            case FIR_POLICE_REPORT -> "FIR / police report";
            case THEFT_EVIDENCE -> "Theft evidence";
            case OTHER -> "Other document";
        };
    }
}
