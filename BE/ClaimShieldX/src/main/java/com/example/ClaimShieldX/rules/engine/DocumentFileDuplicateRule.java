package com.example.ClaimShieldX.rules.engine;

import com.example.ClaimShieldX.documents.model.DocumentType;
import com.example.ClaimShieldX.rules.config.RuleProperties;
import com.example.ClaimShieldX.rules.engine.RuleInputs.DocumentFacts;
import com.example.ClaimShieldX.rules.engine.RuleInputs.FileMatch;
import com.example.ClaimShieldX.rules.model.EvidenceRef;
import com.example.ClaimShieldX.rules.model.InputReliability;
import com.example.ClaimShieldX.rules.model.RuleDefinition;
import com.example.ClaimShieldX.rules.model.RuleResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Finds files with exactly the same bytes (SHA-256) elsewhere in the organization. It does not call every repeat suspicious:
 * a replaced earlier version of the same document on this claim is expected, and a policy record, registration copy or licence
 * is routinely attached to several claims of the same policy, vehicle or driver. Only byte-identical files are found: a
 * re-scan or a compressed copy of the same page is a different file and is not detected here.
 */
public final class DocumentFileDuplicateRule implements Rule {
    public static final RuleDefinition DEFINITION = new RuleDefinition("DOCUMENT_FILE_DUPLICATE", "1", "Exact file duplicate",
            "Finds documents that are byte-for-byte identical to another document in the organization, and says whether the repeat is on this claim, on another claim, or an earlier version that was replaced.",
            List.of("At least one document on the claim"),
            List.of("A document was re-uploaded after a mistake, or an earlier version was restored.", "Reference documents (policy, registration, licence) are legitimately shared between claims.",
                    "A corrected or reissued document usually differs in content; an identical file is more often a re-upload than a reissue."));

    private static final Set<DocumentType> REUSABLE = EnumSet.of(DocumentType.POLICY_RECORD, DocumentType.VEHICLE_REGISTRATION, DocumentType.DRIVING_LICENCE, DocumentType.OTHER);

    @Override
    public RuleDefinition definition() {
        return DEFINITION;
    }

    @Override
    public RuleResult evaluate(RuleInputs inputs, RuleProperties config) {
        List<DocumentFacts> current = inputs.documents().stream().filter(d -> !d.superseded()).sorted(Comparator.comparing(DocumentFacts::id)).toList();
        if (current.isEmpty()) {
            return Results.incomplete(DEFINITION, "The claim has no documents yet, so there is nothing to compare for exact duplicates.", List.of("DOCUMENTS"), Results.values(), List.of());
        }
        Map<java.util.UUID, DocumentFacts> byId = new TreeMap<>();
        current.forEach(d -> byId.put(d.id(), d));

        List<Map<String, Object>> matches = new ArrayList<>();
        List<EvidenceRef> evidence = new ArrayList<>();
        Map<String, Integer> byClass = new TreeMap<>();
        boolean material = false;
        List<FileMatch> ordered = inputs.fileMatches().stream().filter(m -> byId.containsKey(m.documentId()))
                .sorted(Comparator.comparing(FileMatch::documentId).thenComparing(FileMatch::otherReceivedAt).thenComparing(FileMatch::otherDocumentId)).toList();
        for (FileMatch m : ordered) {
            DocumentFacts doc = byId.get(m.documentId());
            boolean sameClaim = m.otherClaimId().equals(inputs.claim().id());
            String kind;
            boolean needsReview;
            if (sameClaim) {
                if (m.otherSuperseded() && m.otherType() == doc.type()) {
                    kind = "EARLIER_VERSION_ON_THIS_CLAIM";
                    needsReview = false;
                } else if (m.otherType() != doc.type()) {
                    kind = "SAME_FILE_UNDER_ANOTHER_TYPE_ON_THIS_CLAIM";
                    needsReview = true;
                } else {
                    kind = "SAME_FILE_TWICE_ON_THIS_CLAIM";
                    needsReview = true;
                }
            } else if (REUSABLE.contains(doc.type()) && m.otherType() == doc.type()) {
                kind = "REFERENCE_DOCUMENT_SHARED_WITH_ANOTHER_CLAIM";
                needsReview = false;
            } else {
                kind = "SAME_FILE_ON_ANOTHER_CLAIM";
                needsReview = true;
            }
            byClass.merge(kind, 1, Integer::sum);
            material |= needsReview;
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("documentId", doc.id().toString());
            entry.put("documentType", doc.type().name());
            entry.put("relation", kind);
            entry.put("otherClaimInternalId", m.otherClaimInternalId());
            entry.put("otherDocumentId", m.otherDocumentId().toString());
            entry.put("otherDocumentType", m.otherType().name());
            entry.put("otherDocumentReplaced", m.otherSuperseded());
            entry.put("otherReceivedAt", m.otherReceivedAt().toString());
            if (!sameClaim) {
                entry.put("sameVehicleToken", m.sameVehicleToken());
                entry.put("samePolicyToken", m.samePolicyToken());
            }
            matches.add(entry);
            evidence.add(EvidenceRef.document(doc.id(), doc.type().name()));
            evidence.add(EvidenceRef.otherClaimDocument(m.otherClaimId(), m.otherDocumentId(), m.otherType().name(), kind));
        }

        Map<String, Object> values = Results.values();
        values.put("documentsChecked", current.size());
        values.put("matches", matches);
        values.put("matchesByRelation", byClass);
        values.put("detects", "byte-identical files only");
        if (matches.isEmpty()) {
            return Results.pass(DEFINITION, InputReliability.RELIABLE, List.of(), current.size() + " document" + (current.size() == 1 ? " has" : "s have")
                    + " no byte-identical copy elsewhere in the organization.", values,
                    current.stream().map(d -> EvidenceRef.document(d.id(), d.type().name())).toList());
        }
        return Results.flag(DEFINITION, Results.severity(material), InputReliability.RELIABLE, List.of(),
                matches.size() + " document match" + (matches.size() == 1 ? "" : "es") + " found with identical file content (" + describe(byClass) + ").", values, evidence);
    }

    private static String describe(Map<String, Integer> byClass) {
        List<String> parts = new ArrayList<>();
        byClass.forEach((kind, count) -> parts.add(count + " " + kind.toLowerCase().replace('_', ' ')));
        return String.join("; ", parts);
    }
}
