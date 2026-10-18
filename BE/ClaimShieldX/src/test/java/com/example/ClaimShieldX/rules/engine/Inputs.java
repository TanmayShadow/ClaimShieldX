package com.example.ClaimShieldX.rules.engine;

import com.example.ClaimShieldX.claims.model.ClaimStage;
import com.example.ClaimShieldX.claims.model.IncidentType;
import com.example.ClaimShieldX.documents.model.DocumentStatus;
import com.example.ClaimShieldX.documents.model.DocumentType;
import com.example.ClaimShieldX.extraction.schema.ExtractionResult;
import com.example.ClaimShieldX.extraction.schema.FieldResult;
import com.example.ClaimShieldX.extraction.schema.FieldState;
import com.example.ClaimShieldX.extraction.schema.IssueCode;
import com.example.ClaimShieldX.extraction.schema.IssueSeverity;
import com.example.ClaimShieldX.extraction.schema.LineItemResult;
import com.example.ClaimShieldX.extraction.schema.ReviewIssue;
import com.example.ClaimShieldX.extraction.schema.Source;
import com.example.ClaimShieldX.readiness.model.EvidenceState;
import com.example.ClaimShieldX.readiness.model.Readiness;
import com.example.ClaimShieldX.readiness.model.RequirementLevel;
import com.example.ClaimShieldX.rules.engine.RuleInputs.ChecklistFacts;
import com.example.ClaimShieldX.rules.engine.RuleInputs.ClaimFacts;
import com.example.ClaimShieldX.rules.engine.RuleInputs.DocumentFacts;
import com.example.ClaimShieldX.rules.engine.RuleInputs.ExtractionFacts;
import com.example.ClaimShieldX.rules.engine.RuleInputs.FileMatch;
import com.example.ClaimShieldX.rules.engine.RuleInputs.HistoryFacts;
import com.example.ClaimShieldX.rules.engine.RuleInputs.InvoiceIdentity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Builds rule inputs for tests: a consistent claim, invoice and estimate that every test then breaks in exactly one way, so
 * each test shows which single change causes which result.
 */
public final class Inputs {
    static final UUID CLAIM_ID = UUID.fromString("00000000-0000-0000-0000-00000000c001");
    public static final Instant INCIDENT = Instant.parse("2026-09-10T05:00:00Z");      // 10:30 on 10 September in India
    public static final Instant T0 = Instant.parse("2026-09-20T10:00:00Z");

    private Inputs() {
    }

    public static final class Doc {
        public final UUID id = UUID.randomUUID();
        public final DocumentType type;
        public DocumentStatus status = DocumentStatus.VERIFIED;
        public String sha256 = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        public Instant receivedAt = T0;
        public boolean superseded;
        public UUID supersedes;
        public boolean extracted = true;
        final Map<String, String> header = new LinkedHashMap<>();
        final Map<String, String> printed = new LinkedHashMap<>();
        final Map<String, String> notes = new LinkedHashMap<>();
        final Set<String> absent = new LinkedHashSet<>();
        final Set<String> unreadable = new LinkedHashSet<>();
        final List<String[]> lines = new ArrayList<>();            // description, category, quantity, unitPrice, tax, lineTotal
        final List<ReviewIssue> issues = new ArrayList<>();
        final UUID extractionId = UUID.randomUUID();

        Doc(DocumentType type) {
            this.type = type;
        }

        public Doc sha(String value) {
            sha256 = value;
            return this;
        }

        public Doc status(DocumentStatus status) {
            this.status = status;
            return this;
        }

        public Doc set(String field, String value) {
            header.put(field, value);
            absent.remove(field);
            unreadable.remove(field);
            return this;
        }

        public Doc absent(String field) {
            header.remove(field);
            absent.add(field);
            return this;
        }

        public Doc unreadable(String field) {
            header.remove(field);
            unreadable.add(field);
            return this;
        }

        public Doc printedAs(String field, String text) {
            printed.put(field, text);
            return this;
        }

        public Doc assumed(String field) {
            notes.put(field, "DAY_FIRST_ASSUMED");
            return this;
        }

        public Doc line(String description, String quantity, String unitPrice, String lineTotal) {
            lines.add(new String[]{description, "PART", quantity, unitPrice, null, lineTotal});
            return this;
        }

        public Doc lineWithTax(String description, String quantity, String unitPrice, String tax, String lineTotal) {
            lines.add(new String[]{description, "PART", quantity, unitPrice, tax, lineTotal});
            return this;
        }

        public Doc clearLines() {
            lines.clear();
            return this;
        }

        public Doc issue(IssueCode code) {
            issues.add(new ReviewIssue(code, IssueSeverity.REVIEW, null, "test"));
            return this;
        }

        public Doc notExtracted() {
            extracted = false;
            return this;
        }

        public Doc received(Instant at) {
            receivedAt = at;
            return this;
        }

        public Doc replaced() {
            superseded = true;
            return this;
        }

        public DocumentFacts facts() {
            ExtractionFacts extraction = null;
            if (extracted) {
                List<FieldResult> fields = new ArrayList<>();
                for (String name : List.of("documentNumber", "documentDate", "incidentDate", "repairStartDate", "repairCompletionDate", "garageIdentifier",
                        "vehicleIdentifier", "currency", "repairDescription", "subtotal", "taxTotal", "discountTotal", "grandTotal")) {
                    fields.add(field(name, header.get(name), absent.contains(name), unreadable.contains(name)));
                }
                List<LineItemResult> items = new ArrayList<>();
                for (int i = 0; i < lines.size(); i++) {
                    String[] l = lines.get(i);
                    String p = "lineItems[" + i + "].";
                    items.add(new LineItemResult(i, List.of(field(p + "description", l[0], l[0] == null, false), field(p + "category", l[1], false, false),
                            field(p + "quantity", l[2], l[2] == null, false), field(p + "unitPrice", l[3], l[3] == null, false),
                            field(p + "tax", l[4], l[4] == null, false), field(p + "lineTotal", l[5], l[5] == null, false))));
                }
                extraction = new ExtractionFacts(extractionId, 1, "itemized-cost-2", new ExtractionResult(fields, items, null, List.of(), issues));
            }
            return new DocumentFacts(id, type, status, sha256, receivedAt, superseded, supersedes, extraction);
        }

        private FieldResult field(String name, String value, boolean isAbsent, boolean isUnreadable) {
            if (value == null) {
                return new FieldResult(name, FieldState.UNKNOWN, null, null, null, isUnreadable ? "ILLEGIBLE" : "NOT_PRESENT", null);
            }
            String shown = printed.getOrDefault(name, value);
            return new FieldResult(name, FieldState.EXTRACTED, shown, value, notes.get(name), null, new Source(id, 1, "text near " + name));
        }
    }

    /** A final invoice that reconciles: 2 x 5,000.00 + 1 x 3,500.00 = 13,500.00, tax 18% on top = 15,930.00. */
    public static Doc invoice() {
        return new Doc(DocumentType.FINAL_INVOICE)
                .set("documentNumber", "INV-2026-0042").set("documentDate", "2026-09-15").set("incidentDate", "2026-09-10")
                .set("garageIdentifier", "GAR-1").set("vehicleIdentifier", "VEH-1").set("currency", "INR")
                .set("subtotal", "13500.00").set("taxTotal", "2430.00").set("discountTotal", "0.00").set("grandTotal", "15930.00")
                .line("Front bumper replacement", "2", "5000.00", "10000.00")
                .line("Headlamp assembly left", "1", "3500.00", "3500.00");
    }

    /** A garage estimate for 15,000.00. */
    public static Doc estimate() {
        return new Doc(DocumentType.GARAGE_ESTIMATE)
                .set("documentNumber", "EST-2026-0007").set("documentDate", "2026-09-12").set("incidentDate", "2026-09-10")
                .set("garageIdentifier", "GAR-1").set("vehicleIdentifier", "VEH-1").set("currency", "INR")
                .set("subtotal", "15000.00").set("grandTotal", "15000.00").absent("taxTotal").absent("discountTotal")
                .line("Front bumper replacement", "2", "5000.00", "10000.00")
                .line("Headlamp assembly left", "1", "5000.00", "5000.00");
    }

    public static Doc plain(DocumentType type) {
        return new Doc(type);
    }

    static ClaimFacts claim() {
        return new ClaimFacts(CLAIM_ID, "CLM-1", 1, ClaimStage.FINAL_INVOICE_RECEIVED, IncidentType.COLLISION, INCIDENT,
                Instant.parse("2026-09-11T05:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-12-31T00:00:00Z"),
                new BigDecimal("15000.00"), null, "INR", "GAR-1", "VEH-1");
    }

    /** A claim whose fields a test changes one at a time. */
    static final class ClaimB {
        ClaimStage stage = ClaimStage.FINAL_INVOICE_RECEIVED;
        Instant incidentAt = INCIDENT;
        Instant reportedAt = Instant.parse("2026-09-11T05:00:00Z");
        Instant policyStart = Instant.parse("2026-01-01T00:00:00Z");
        Instant policyEnd = Instant.parse("2026-12-31T00:00:00Z");
        BigDecimal initial = new BigDecimal("15000.00");
        BigDecimal approved;
        String currency = "INR";
        String garage = "GAR-1";

        ClaimB garage(String v) {
            garage = v;
            return this;
        }

        ClaimB stage(ClaimStage v) {
            stage = v;
            return this;
        }

        ClaimB incident(String instant) {
            incidentAt = Instant.parse(instant);
            return this;
        }

        ClaimB reported(String instant) {
            reportedAt = instant == null ? null : Instant.parse(instant);
            return this;
        }

        ClaimB policy(String start, String end) {
            policyStart = start == null ? null : Instant.parse(start);
            policyEnd = end == null ? null : Instant.parse(end);
            return this;
        }

        ClaimB initial(String amount) {
            initial = amount == null ? null : new BigDecimal(amount);
            return this;
        }

        ClaimB approved(String amount) {
            approved = amount == null ? null : new BigDecimal(amount);
            return this;
        }

        ClaimB currency(String v) {
            currency = v;
            return this;
        }

        ClaimFacts build() {
            return new ClaimFacts(CLAIM_ID, "CLM-1", 1, stage, IncidentType.COLLISION, incidentAt, reportedAt, policyStart, policyEnd, initial, approved, currency, garage, "VEH-1");
        }
    }

    static ClaimB claimB() {
        return new ClaimB();
    }

    static ClaimFacts claim(java.util.function.UnaryOperator<ClaimFacts> change) {
        return change.apply(claim());
    }

    /** The checklist for the final-invoice stage: invoice and estimate required, claim form optional. */
    static ChecklistFacts checklist() {
        return new ChecklistFacts(Readiness.READY, List.of(
                new ChecklistFacts.Entry(DocumentType.FINAL_INVOICE, RequirementLevel.REQUIRED, true, EvidenceState.VERIFIED, List.of(), List.of()),
                new ChecklistFacts.Entry(DocumentType.GARAGE_ESTIMATE, RequirementLevel.REQUIRED, false, EvidenceState.VERIFIED, List.of(), List.of()),
                new ChecklistFacts.Entry(DocumentType.CLAIM_FORM, RequirementLevel.OPTIONAL, false, EvidenceState.NOT_RECEIVED, List.of(), List.of())), List.of());
    }

    static ChecklistFacts checklist(Readiness readiness, List<ChecklistFacts.Entry> entries, List<ChecklistFacts.Issue> issues) {
        return new ChecklistFacts(readiness, entries, issues);
    }

    static ChecklistFacts.Entry entry(DocumentType type, RequirementLevel level) {
        return new ChecklistFacts.Entry(type, level, false, EvidenceState.NOT_RECEIVED, List.of(), List.of());
    }

    static final UUID OTHER_CLAIM = UUID.fromString("00000000-0000-0000-0000-00000000c002");

    /** The same bytes as {@code doc} exist as a document of type {@code otherType} on another claim (or this one, if {@code claimId} is this claim). */
    static FileMatch fileMatch(Doc doc, UUID claimId, DocumentType otherType, boolean otherReplaced) {
        return new FileMatch(doc.id, UUID.randomUUID(), claimId, claimId.equals(CLAIM_ID) ? "CLM-1" : "CLM-2", otherType, DocumentStatus.VERIFIED, otherReplaced,
                null, T0.minusSeconds(86_400), false, false);
    }

    /** An invoice on another claim with the given number, issuer, total and date. */
    static InvoiceIdentity identity(UUID claimId, String number, String issuer, String total, String date) {
        return identity(claimId, number, issuer, "GAR-1", total, date);
    }

    /** As above, with the garage recorded on that invoice's claim given separately from the identifier printed on the invoice. */
    static InvoiceIdentity identity(UUID claimId, String number, String issuer, String claimGarage, String total, String date) {
        return new InvoiceIdentity(claimId, claimId.equals(CLAIM_ID) ? "CLM-1" : "CLM-2", UUID.randomUUID(), UUID.randomUUID(), DocumentStatus.VERIFIED, false, null,
                T0.minusSeconds(86_400), number, issuer, claimGarage, total == null ? null : new BigDecimal(total), "INR", date, false, false);
    }

    static HistoryFacts.HistoryMatch historyMatch(String relation, String strength, String timing, Integer days) {
        return new HistoryFacts.HistoryMatch("CLM-OLD-" + relation + days, 1, relation, strength, timing, days, "CLOSED_PAID", "NO_ACTION", List.of(), UUID.randomUUID(), 7, "HC-0007");
    }

    static HistoryFacts history(String status, HistoryFacts.HistoryMatch... matches) {
        return new HistoryFacts(status, UUID.randomUUID(), false, matches.length, false, List.of(matches));
    }

    static HistoryFacts noHistory() {
        return new HistoryFacts("NO_HISTORICAL_DATA_AVAILABLE", null, false, 0, false, List.of());
    }

    static final class Builder {
        ClaimFacts claim = Inputs.claim();
        ChecklistFacts checklist = Inputs.checklist();
        final List<DocumentFacts> documents = new ArrayList<>();
        final List<FileMatch> fileMatches = new ArrayList<>();
        final List<InvoiceIdentity> candidates = new ArrayList<>();
        HistoryFacts history = Inputs.noHistory();

        Builder claim(ClaimFacts claim) {
            this.claim = claim;
            return this;
        }

        Builder checklist(ChecklistFacts checklist) {
            this.checklist = checklist;
            return this;
        }

        Builder with(Doc... docs) {
            for (Doc d : docs) {
                documents.add(d.facts());
            }
            return this;
        }

        Builder match(FileMatch match) {
            fileMatches.add(match);
            return this;
        }

        Builder candidate(InvoiceIdentity candidate) {
            candidates.add(candidate);
            return this;
        }

        Builder history(HistoryFacts history) {
            this.history = history;
            return this;
        }

        RuleInputs build() {
            return new RuleInputs(claim, checklist, documents, fileMatches, candidates, history);
        }
    }

    static Builder inputs() {
        return new Builder();
    }
}
