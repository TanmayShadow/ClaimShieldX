package com.example.ClaimShieldX.analysis.findings;

import com.example.ClaimShieldX.analysis.reasoning.HypothesisValidator.Assessment;
import com.example.ClaimShieldX.analysis.reasoning.HypothesisValidator.Hypothesis;
import com.example.ClaimShieldX.analysis.reasoning.HypothesisValidator.Review;
import com.example.ClaimShieldX.analysis.snapshot.SnapshotService.Item;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns the snapshot's rule results and the reviewed hypotheses into findings. Deterministic findings are the rules' own results in
 * plain words with the harmless explanations to rule out; a rule that could not run becomes an UNKNOWN finding, never a negative one.
 * Nothing here is a verdict: wording is neutral and a recommendation is a next check.
 */
public final class FindingFactory {
    private record Text(String category, String title, String recommendation) {
    }

    private static final Map<String, Text> RULES = Map.of(
            "INVOICE_ARITHMETIC", new Text("ARITHMETIC", "Invoice figures do not reconcile", "Compare the printed totals with the invoice and ask the garage to explain the difference."),
            "ESTIMATE_INVOICE_VARIANCE", new Text("VARIANCE", "Invoice total is above the estimate", "Ask for the supplementary estimate or approval that covers the increase."),
            "DATE_CONSISTENCY", new Text("DATES", "Dates do not agree", "Check the dates against the original documents and the claim record."),
            "DOCUMENT_FILE_DUPLICATE", new Text("DUPLICATE_FILE", "An identical file exists elsewhere", "Compare the claims that hold the same file and confirm which is genuine."),
            "INVOICE_IDENTITY_DUPLICATE", new Text("DUPLICATE_INVOICE", "Another invoice has the same number and issuer", "Compare the invoices and ask the garage whether one is a correction or reissue."),
            "REPEATED_LINE_ITEMS", new Text("REPEATED_LINES", "Line items are repeated", "Ask the garage to confirm that each repeated line is a separate repair."),
            "HISTORICAL_RELATIONSHIP", new Text("HISTORY", "Earlier claims relate to this one", "Review the earlier claims and check whether the earlier damage was repaired."));

    private FindingFactory() {
    }


    public static List<FindingDraft> deterministic(List<Item> items) {
        Set<String> refs = new LinkedHashSet<>(items.stream().map(Item::ref).toList());
        List<FindingDraft> drafts = new ArrayList<>();
        for (Item item : items) {
            if (!item.ref().startsWith("RULE.")) {
                continue;
            }
            String ruleId = String.valueOf(item.data().get("ruleId"));
            String outcome = String.valueOf(item.data().get("outcome"));
            String explanation = String.valueOf(item.data().get("explanation"));
            if (outcome.equals("FLAG") && RULES.containsKey(ruleId)) {
                Text text = RULES.get(ruleId);
                boolean material = "REVIEW".equals(item.data().get("severity"));
                List<String> support = new ArrayList<>(List.of(item.ref()));
                for (Item other : items) {
                    if (other.type().equals("RELATIONSHIP") && other.data().get("evidenceRefs") instanceof List<?> l && l.contains(item.ref())) {
                        support.add(other.ref());
                    }
                }
                drafts.add(new FindingDraft("RULE:" + ruleId, text.category(), text.title(), explanation, "FACT", material ? "NEEDS_REVIEW" : "NOTED", "VALIDATED", null,
                        support.stream().filter(refs::contains).distinct().toList(), List.of(), considerations(ruleId), List.of(), text.recommendation()));
            } else if (outcome.equals("INCOMPLETE")) {
                List<String> missing = item.data().get("values") instanceof Map<?, ?> v && v.get("missing") instanceof List<?> m ? m.stream().map(String::valueOf).toList() : List.of();
                String title = ruleId.equals("REQUIRED_EVIDENCE") ? "Some expected documents are missing or need review" : "A check could not run: " + ruleId;
                drafts.add(new FindingDraft("RULE:" + ruleId, "EVIDENCE_GAP", title, explanation, "UNKNOWN", "UNKNOWN", "VALIDATED", null, List.of(item.ref()), List.of(), List.of(),
                        missing, "Obtain the missing evidence, then run the investigation again. This is a gap in the evidence, not a finding about the claim."));
            }
        }
        return drafts;
    }

    private static List<String> considerations(String ruleId) {
        return switch (ruleId) {
            case "INVOICE_ARITHMETIC" -> List.of("Tax, a discount or a round-off line may be printed differently from how it was read.", "A figure may have been misread from the document.");
            case "ESTIMATE_INVOICE_VARIANCE" -> List.of("Supplementary damage or a revised estimate may explain an increase.", "Hidden damage found on dismantling, or tax and labour-rate changes.");
            case "DATE_CONSISTENCY" -> List.of("A typing or format error, or a time-zone effect near midnight.", "Delayed reporting, or a document dated when it was issued.");
            case "DOCUMENT_FILE_DUPLICATE" -> List.of("A document may have been uploaded again by mistake.", "Reference documents are legitimately shared between claims of one policy.");
            case "INVOICE_IDENTITY_DUPLICATE" -> List.of("A reissued or corrected invoice looks the same.", "An issuer may restart its numbering.");
            case "REPEATED_LINE_ITEMS" -> List.of("Identical parts or labour can legitimately appear more than once.", "The garage's software may copy a description across rows.");
            case "HISTORICAL_RELATIONSHIP" -> List.of("The earlier damage may have been repaired completely or may be different damage.", "A fleet, a family policy or a shared token links claims without meaning anything.");
            default -> List.of();
        };
    }

    /** A hypothesis becomes a published finding only when the reviewer supports it at least in part. */
    public static FindingDraft hypothesis(Hypothesis h, Assessment assessment, Review review, boolean reviewAvailable) {
        List<String> counterEvidence = assessment == null ? List.of() : assessment.legitimate().stream().map(l -> l.explanation()).toList();
        List<String> counterRefs = assessment == null ? List.of() : assessment.counterEvidenceRefs();
        List<String> checks = new ArrayList<>(h.checksNeeded());
        if (assessment != null) {
            assessment.legitimate().forEach(l -> checks.addAll(l.checksNeeded()));
        }
        String reason = !reviewAvailable ? "REVIEW_UNAVAILABLE" : review == null ? "NOT_REVIEWED" : review.support().equals("NOT_SUPPORTED") ? "NOT_SUPPORTED_BY_EVIDENCE" : null;
        String status = review == null ? "UNREVIEWED" : review.support();
        String summary = h.explanation() + (review != null && !review.reasons().isEmpty() ? " Reviewer: " + String.join(" ", review.reasons()) : "");
        return new FindingDraft("AI:" + h.id(), "HYPOTHESIS", h.title(), summary, "HYPOTHESIS", status, reason == null ? "VALIDATED" : "REJECTED", reason,
                h.supportingRefs(), counterRefs, counterEvidence, checks.stream().distinct().limit(6).toList(),
                "Check the supporting evidence and rule out the legitimate explanations listed. This is a hypothesis to verify, not a conclusion.");
    }
}
