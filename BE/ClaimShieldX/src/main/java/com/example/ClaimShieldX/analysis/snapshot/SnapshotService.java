package com.example.ClaimShieldX.analysis.snapshot;

import com.example.ClaimShieldX.analysis.Json;
import com.example.ClaimShieldX.claims.model.Claim;
import com.example.ClaimShieldX.claims.repository.ClaimsRepository;
import com.example.ClaimShieldX.documents.model.Document;
import com.example.ClaimShieldX.documents.model.DocumentStatus;
import com.example.ClaimShieldX.documents.model.DocumentType;
import com.example.ClaimShieldX.documents.repository.DocumentRepository;
import com.example.ClaimShieldX.extraction.model.DocumentExtraction;
import com.example.ClaimShieldX.extraction.model.ExtractionRunStatus;
import com.example.ClaimShieldX.extraction.repository.DocumentExtractionRepository;
import com.example.ClaimShieldX.extraction.schema.FieldResult;
import com.example.ClaimShieldX.extraction.schema.FieldState;
import com.example.ClaimShieldX.extraction.schema.LineItemResult;
import com.example.ClaimShieldX.rules.model.RuleOutcome;
import com.example.ClaimShieldX.rules.model.RuleRun;
import com.example.ClaimShieldX.rules.model.RuleResultRow;
import com.example.ClaimShieldX.rules.repository.RuleResultRepository;
import com.example.ClaimShieldX.rules.repository.RuleRunRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Freezes the evidence of one investigation. Each item has a stable reference that is unique inside the snapshot, and everything later
 * (findings, model answers) may cite only references of its own snapshot. The snapshot is built from stored data only: the claim, the
 * documents, the stored extractions, the stored rule run. Nothing is read from a document again and no model is called.
 *
 * <p>Items that came from documents (extracted fields) carry the printed and the normalized value but never the model's source quote,
 * because a quote is document text and could carry instructions. Nothing created later changes a snapshot.
 */
@Service
public class SnapshotService {
    public static final List<String> HEADER_FIELDS = List.of("documentNumber", "documentDate", "incidentDate", "repairStartDate", "repairCompletionDate",
            "garageIdentifier", "vehicleIdentifier", "currency", "subtotal", "taxTotal", "discountTotal", "grandTotal");

    public record Item(String ref, String type, String state, String label, Map<String, Object> data) {
    }

    public record Snapshot(UUID id, Instant createdAt, UUID investigationId, UUID claimId, long claimVersion, UUID ruleRunId, String ruleSetVersion,
                           String inputFingerprint, UUID historyImportId, int itemCount) {
    }

    private final JdbcTemplate jdbc;
    private final ClaimsRepository claimsRepository;
    private final DocumentRepository documentRepository;
    private final DocumentExtractionRepository extractionRepository;
    private final RuleRunRepository ruleRunRepository;
    private final RuleResultRepository ruleResultRepository;

    public SnapshotService(JdbcTemplate jdbc, ClaimsRepository claimsRepository, DocumentRepository documentRepository,
                           DocumentExtractionRepository extractionRepository, RuleRunRepository ruleRunRepository, RuleResultRepository ruleResultRepository) {
        this.jdbc = jdbc;
        this.claimsRepository = claimsRepository;
        this.documentRepository = documentRepository;
        this.extractionRepository = extractionRepository;
        this.ruleRunRepository = ruleRunRepository;
        this.ruleResultRepository = ruleResultRepository;
    }

    /** Builds the snapshot of an investigation, or returns the one it already has (running a stage twice never makes two). */
    @Transactional
    public UUID freeze(UUID organizationId, UUID claimId, UUID investigationId, UUID ruleRunId) {
        Optional<Snapshot> existing = find(organizationId, investigationId);
        if (existing.isPresent()) {
            return existing.get().id();
        }
        Claim claim = claimsRepository.findByIdAndOrganizationId(claimId, organizationId).orElseThrow(() -> new IllegalStateException("claim missing"));
        RuleRun run = ruleRunRepository.findByIdAndOrganizationIdAndClaimId(ruleRunId, organizationId, claimId).orElseThrow(() -> new IllegalStateException("rule run missing"));
        List<RuleResultRow> results = ruleResultRepository.findByOrganizationIdAndRunId(organizationId, ruleRunId);
        List<Document> documents = documentRepository.findByOrganizationIdAndClaimIdOrderByReceivedAtAscIdAsc(organizationId, claimId);

        List<Item> items = new ArrayList<>();
        claimItems(claim, items);
        documentItems(organizationId, documents, items);
        ruleItems(results, items);
        UUID historyImport = historyItems(results, items);
        gapItems(results, items);
        relationshipItems(results, documents, items);

        UUID snapshotId = UUID.randomUUID();
        jdbc.update("""
                insert into evidence_snapshots(id, organization_id, claim_id, investigation_id, claim_version, rule_run_id, rule_set_version, input_fingerprint, history_import_id, item_count)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, snapshotId, organizationId, claimId, investigationId, claim.getClaimVersion(), ruleRunId, run.getRuleSetVersion(), run.getInputFingerprint(), historyImport, items.size());
        for (Item item : items) {
            jdbc.update("insert into evidence_items(id, snapshot_id, organization_id, claim_id, ref, item_type, state, label, data) values (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)",
                    UUID.randomUUID(), snapshotId, organizationId, claimId, item.ref(), item.type(), item.state(), cut(item.label(), 300), Json.write(item.data()));
        }
        return snapshotId;
    }

    @Transactional(readOnly = true)
    public Optional<Snapshot> find(UUID organizationId, UUID investigationId) {
        return jdbc.query("select id, created_at, investigation_id, claim_id, claim_version, rule_run_id, rule_set_version, input_fingerprint, history_import_id, item_count from evidence_snapshots where organization_id = ? and investigation_id = ?",
                (rs, n) -> new Snapshot(rs.getObject(1, UUID.class), rs.getTimestamp(2).toInstant(), rs.getObject(3, UUID.class), rs.getObject(4, UUID.class), rs.getLong(5),
                        rs.getObject(6, UUID.class), rs.getString(7), rs.getString(8), rs.getObject(9, UUID.class), rs.getInt(10)), organizationId, investigationId).stream().findFirst();
    }

    @Transactional(readOnly = true)
    public List<Item> items(UUID organizationId, UUID snapshotId) {
        return jdbc.query("select ref, item_type, state, label, data::text from evidence_items where organization_id = ? and snapshot_id = ? order by ref",
                (rs, n) -> new Item(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), Json.map(rs.getString(5))), organizationId, snapshotId);
    }

    // ------------------------------------------------------------------------------------------------ claim

    private static void claimItems(Claim claim, List<Item> items) {
        var incident = claim.getIncident();
        var financial = claim.getFinancial();
        var policy = claim.getPolicy();
        items.add(fact("CLAIM.stage", "Claim stage", Map.of("value", String.valueOf(claim.getCurrentStage()))));
        if (incident != null) {
            items.add(fact("CLAIM.incidentType", "Incident type", Map.of("value", String.valueOf(incident.getIncidentType()))));
            if (incident.getIncidentAt() != null) {
                items.add(fact("CLAIM.incidentAt", "Incident date and time", Map.of("value", incident.getIncidentAt().toString())));
            }
            if (incident.getReportedAt() != null) {
                items.add(fact("CLAIM.reportedAt", "Date and time the claim was reported", Map.of("value", incident.getReportedAt().toString())));
            }
        }
        if (policy != null && (policy.getPolicyStartDate() != null || policy.getPolicyEndDate() != null)) {
            Map<String, Object> period = new LinkedHashMap<>();
            period.put("start", policy.getPolicyStartDate() == null ? null : policy.getPolicyStartDate().toString());
            period.put("end", policy.getPolicyEndDate() == null ? null : policy.getPolicyEndDate().toString());
            items.add(fact("CLAIM.policyPeriod", "Policy period", period));
        }
        if (financial != null) {
            if (financial.getInitialEstimateAmount() != null) {
                items.add(fact("CLAIM.initialEstimate", "Initial estimate recorded on the claim", Map.of("amount", financial.getInitialEstimateAmount().toPlainString(), "currency", String.valueOf(financial.getCurrency()))));
            }
            if (financial.getApprovedEstimateAmount() != null) {
                items.add(fact("CLAIM.approvedEstimate", "Approved estimate recorded on the claim", Map.of("amount", financial.getApprovedEstimateAmount().toPlainString(), "currency", String.valueOf(financial.getCurrency()))));
            }
        }
    }

    private static Item fact(String ref, String label, Map<String, Object> data) {
        return new Item(ref, "CLAIM_FIELD", "VERIFIED_FACT", label, data);
    }

    // ------------------------------------------------------------------------------------------------ documents and their extracted fields

    static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }

    private void documentItems(UUID organizationId, List<Document> documents, List<Item> items) {
        Map<UUID, DocumentExtraction> newest = documents.isEmpty() ? Map.of()
                : extractionRepository.findNewestByDocuments(organizationId, documents.stream().map(Document::getId).toList()).stream()
                .collect(Collectors.toMap(DocumentExtraction::getDocumentId, Function.identity()));
        for (Document d : documents) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("documentId", d.getId().toString());
            data.put("documentType", d.getDocumentType().name());
            data.put("status", d.getStatus().name());
            data.put("superseded", d.isSuperseded());
            data.put("receivedAt", d.getReceivedAt().toString());
            data.put("sha256", d.getSha256().substring(0, 16));
            items.add(new Item("DOC." + shortId(d.getId()), "DOCUMENT", "VERIFIED_FACT", label(d.getDocumentType()) + " received", data));

            DocumentExtraction extraction = newest.get(d.getId());
            boolean read = !d.isSuperseded() && extraction != null && extraction.getStatus() == ExtractionRunStatus.SUCCEEDED && extraction.getResult() != null
                    && (d.getDocumentType() == DocumentType.FINAL_INVOICE || d.getDocumentType() == DocumentType.GARAGE_ESTIMATE);
            if (!read) {
                continue;
            }
            String state = d.getStatus() == DocumentStatus.NEEDS_REVIEW ? "EXTRACTED_NEEDS_REVIEW" : "EXTRACTED";
            for (FieldResult f : extraction.getResult().fields()) {
                if (HEADER_FIELDS.contains(f.name()) && f.state() == FieldState.EXTRACTED && f.normalizedValue() != null) {
                    Map<String, Object> field = new LinkedHashMap<>();
                    field.put("documentId", d.getId().toString());
                    field.put("documentType", d.getDocumentType().name());
                    field.put("field", f.name());
                    field.put("printedValue", cut(f.value(), 120));
                    field.put("value", f.normalizedValue());
                    if (f.normalizationNote() != null) {
                        field.put("normalizationNote", f.normalizationNote());
                    }
                    items.add(new Item("FIELD." + shortId(d.getId()) + "." + f.name(), "EXTRACTED_FIELD", state, label(d.getDocumentType()) + ": " + f.name(), field));
                }
            }
            List<Map<String, Object>> lines = new ArrayList<>();
            for (LineItemResult line : extraction.getResult().lineItems() == null ? List.<LineItemResult>of() : extraction.getResult().lineItems()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("line", line.index() + 1);
                for (FieldResult cell : line.fields()) {
                    String name = cell.name().substring(cell.name().indexOf('.') + 1);
                    if (cell.state() == FieldState.EXTRACTED && cell.normalizedValue() != null && Set.of("description", "quantity", "unitPrice", "lineTotal").contains(name)) {
                        row.put(name, name.equals("description") ? cut(cell.normalizedValue(), 80) : cell.normalizedValue());
                    }
                }
                lines.add(row);
            }
            if (!lines.isEmpty()) {
                items.add(new Item("LINES." + shortId(d.getId()), "EXTRACTED_FIELD", state, label(d.getDocumentType()) + ": line items", Map.of("documentId", d.getId().toString(), "lines", lines)));
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ rule results, history, gaps, relationships

    private static void ruleItems(List<RuleResultRow> results, List<Item> items) {
        for (RuleResultRow r : results) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("ruleId", r.getRuleId());
            data.put("ruleVersion", r.getRuleVersion());
            data.put("outcome", r.getOutcome().name());
            data.put("severity", r.getSeverity() == null ? null : r.getSeverity().name());
            data.put("reliability", r.getReliability().name());
            data.put("reliabilityReasons", r.getReliabilityReasons());
            data.put("explanation", r.getExplanation());
            data.put("values", r.getValues());
            data.put("ruleResultId", r.getId().toString());
            items.add(new Item("RULE." + r.getRuleId(), "RULE_RESULT", "DETERMINISTIC_RESULT", r.getRuleId() + ": " + r.getOutcome(), data));
        }
    }

    @SuppressWarnings("unchecked")
    private static UUID historyItems(List<RuleResultRow> results, List<Item> items) {
        UUID importId = null;
        for (RuleResultRow r : results) {
            if (!r.getRuleId().equals("HISTORICAL_RELATIONSHIP")) {
                continue;
            }
            Object latest = r.getValues().get("latestClaimsImportId");
            if (latest instanceof String s) {
                importId = UUID.fromString(s);
            }
            Object matches = r.getValues().get("matches");
            if (matches instanceof List<?> list) {
                for (int i = 0; i < list.size() && i < r.getEvidence().size(); i++) {
                    var evidence = r.getEvidence().get(i);
                    if (evidence.importId() == null || !(list.get(i) instanceof Map<?, ?> match)) {
                        continue;
                    }
                    Map<String, Object> data = new LinkedHashMap<>((Map<String, Object>) match);
                    data.put("importId", evidence.importId().toString());
                    data.put("rowNumber", evidence.rowNumber());
                    data.put("sourceRecordId", evidence.sourceRecordId());
                    items.add(new Item("HIST." + shortId(evidence.importId()) + "." + evidence.rowNumber(), "HISTORICAL_RECORD", "CONTEXT",
                            "Earlier claim " + match.get("externalClaimId") + " (" + match.get("relation") + ")", data));
                }
            }
        }
        return importId;
    }

    @SuppressWarnings("unchecked")
    private static void gapItems(List<RuleResultRow> results, List<Item> items) {
        for (RuleResultRow r : results) {
            if (r.getOutcome() != RuleOutcome.INCOMPLETE) {
                continue;
            }
            if (r.getRuleId().equals("REQUIRED_EVIDENCE")) {
                Object issues = r.getValues().get("issues");
                if (issues instanceof List<?> list) {
                    for (Object o : list) {
                        if (o instanceof Map<?, ?> issue) {
                            String state = String.valueOf(issue.get("code")).equals("DOCUMENT_NEEDS_REVIEW") ? "CONTEXT" : "UNKNOWN";
                            items.add(new Item("CHECK." + issue.get("documentType") + "." + issue.get("code"), "CHECKLIST_ITEM", state,
                                    issue.get("documentType") + ": " + issue.get("code"), (Map<String, Object>) issue));
                        }
                    }
                }
                continue;
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("ruleId", r.getRuleId());
            data.put("missing", r.getValues().get("missing"));
            data.put("explanation", r.getExplanation());
            items.add(new Item("GAP." + r.getRuleId(), "UNKNOWN", "UNKNOWN", r.getRuleId() + " could not run", data));
        }
    }

    @SuppressWarnings("unchecked")
    private static void relationshipItems(List<RuleResultRow> results, List<Document> documents, List<Item> items) {
        int n = 1;
        for (RuleResultRow r : results) {
            if (r.getOutcome() != RuleOutcome.FLAG || !(r.getValues().get("matches") instanceof List<?> matches)) {
                continue;
            }
            for (Object o : matches) {
                if (!(o instanceof Map<?, ?> match)) {
                    continue;
                }
                Map<String, Object> edge = new LinkedHashMap<>();
                edge.put("source", "CLAIM");
                List<String> refs = new ArrayList<>(List.of("RULE." + r.getRuleId()));
                switch (r.getRuleId()) {
                    case "DOCUMENT_FILE_DUPLICATE" -> {
                        edge.put("target", "CLAIM:" + match.get("otherClaimInternalId"));
                        edge.put("relationship", "SAME_FILE");
                        edge.put("method", "SHA256_EXACT");
                        edge.put("fields", List.of("sha256"));
                        edge.put("uncertainty", "A re-upload or a shared reference document also produces identical files: " + match.get("relation"));
                        documents.stream().filter(d -> d.getId().toString().equals(match.get("documentId"))).findFirst().ifPresent(d -> refs.add("DOC." + shortId(d.getId())));
                    }
                    case "INVOICE_IDENTITY_DUPLICATE" -> {
                        edge.put("target", "CLAIM:" + match.get("claimInternalId"));
                        edge.put("relationship", "SAME_INVOICE_NUMBER_AND_ISSUER");
                        edge.put("method", "INVOICE_NUMBER_AND_ISSUER");
                        edge.put("fields", List.of("documentNumber", "issuer"));
                        edge.put("uncertainty", "SAME_NUMBER_ISSUER_DIFFERENT_AMOUNT".equals(match.get("level")) ? "A corrected or reissued invoice looks the same" : "Same amount does not by itself show duplication; check the claims");
                    }
                    case "HISTORICAL_RELATIONSHIP" -> {
                        edge.put("target", "HISTORY:" + match.get("externalClaimId"));
                        edge.put("relationship", match.get("relation"));
                        edge.put("method", "EXACT_TOKEN");
                        edge.put("fields", List.of(String.valueOf(match.get("relation")).equals("SAME_VEHICLE") ? "vehicleToken" : String.valueOf(match.get("relation")).equals("SAME_POLICY") ? "policyToken" : "claimantToken"));
                        edge.put("uncertainty", "Strength " + match.get("strength") + "; " + (match.get("ambiguities") instanceof List<?> a && !a.isEmpty() ? a : "no recorded ambiguity"));
                    }
                    default -> {
                        continue;
                    }
                }
                edge.put("evidenceRefs", refs);
                items.add(new Item("REL." + n++, "RELATIONSHIP", "CONTEXT", edge.get("relationship") + " with " + edge.get("target"), edge));
            }
        }
    }

    private static String label(DocumentType type) {
        return switch (type) {
            case FINAL_INVOICE -> "Final invoice";
            case GARAGE_ESTIMATE -> "Garage estimate";
            default -> type.name();
        };
    }

    private static String cut(String text, int max) {
        return text == null ? null : text.length() <= max ? text : text.substring(0, max);
    }
}
