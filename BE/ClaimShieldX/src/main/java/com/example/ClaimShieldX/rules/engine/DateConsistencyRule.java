package com.example.ClaimShieldX.rules.engine;

import com.example.ClaimShieldX.documents.model.DocumentType;
import com.example.ClaimShieldX.extraction.schema.IssueCode;
import com.example.ClaimShieldX.rules.config.RuleProperties;
import com.example.ClaimShieldX.rules.engine.RuleInputs.ClaimFacts;
import com.example.ClaimShieldX.rules.engine.RuleInputs.DocumentFacts;
import com.example.ClaimShieldX.rules.model.EvidenceRef;
import com.example.ClaimShieldX.rules.model.InputReliability;
import com.example.ClaimShieldX.rules.model.RuleDefinition;
import com.example.ClaimShieldX.rules.model.RuleResult;
import com.example.ClaimShieldX.rules.model.RuleSeverity;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compares the dates of the claim record and of the extracted documents, by calendar day in the configured zone. A
 * discrepancy is "material" (severity REVIEW) only if nothing benign explains it: the printed format was not ambiguous, reading
 * day and month the other way round would not reconcile it, a one-day gap is not just a midnight time-zone effect, and the
 * document was read reliably. Otherwise the discrepancy is still listed, with the reason it is not yet material.
 * Policy dates are compared by calendar day, inclusive of both ends.
 */
public final class DateConsistencyRule implements Rule {
    public static final RuleDefinition DEFINITION = new RuleDefinition("DATE_CONSISTENCY", "1", "Date consistency",
            "Checks that the incident, report, policy and document dates are in a plausible order and agree across the claim record and the invoice and estimate.",
            List.of("The claim record (always)", "Read documents add comparisons: final invoice, garage estimate"),
            List.of("A typing or format error: day and month read the wrong way round, or a two-digit year.", "A time-zone effect for events close to midnight.",
                    "Delayed reporting, or a document dated when it was issued rather than when the event happened."));

    private static final Set<IssueCode> OWN_FINDINGS = Set.of(IssueCode.DOCUMENT_DATE_BEFORE_INCIDENT, IssueCode.INCIDENT_DATE_MISMATCH, IssueCode.REPAIR_DATES_OUT_OF_ORDER);
    private static final Pattern NUMERIC_DATE = Pattern.compile("^\\s*(\\d{1,2})[./\\-](\\d{1,2})[./\\-](\\d{4}|\\d{2})\\s*$");

    private enum Kind { SAME_DAY, NOT_BEFORE }

    @Override
    public RuleDefinition definition() {
        return DEFINITION;
    }

    @Override
    public RuleResult evaluate(RuleInputs inputs, RuleProperties config) {
        ClaimFacts claim = inputs.claim();
        ZoneId zone = config.timeZone();
        LocalDate incident = claim.incidentAt().atZone(zone).toLocalDate();
        Collector col = new Collector(config, zone);

        if (claim.reportedAt() != null) {
            col.compare("REPORTED_NOT_BEFORE_INCIDENT", "Claim record", "report date", claim.reportedAt().atZone(zone).toLocalDate(),
                    "incident date", incident, Kind.NOT_BEFORE, List.of(), false, null, List.of(claim.incidentAt(), claim.reportedAt()),
                    List.of(EvidenceRef.claimField("incident.reportedAt"), EvidenceRef.claimField("incident.incidentAt")));
        }
        if (claim.policyStart() != null) {
            col.compare("INCIDENT_NOT_BEFORE_POLICY_START", "Claim record", "incident date", incident, "policy start date",
                    claim.policyStart().atZone(zone).toLocalDate(), Kind.NOT_BEFORE, List.of(), false, null, List.of(claim.incidentAt()),
                    List.of(EvidenceRef.claimField("incident.incidentAt"), EvidenceRef.claimField("policy.policyStartDate")));
        }
        if (claim.policyEnd() != null) {
            col.compare("INCIDENT_NOT_AFTER_POLICY_END", "Claim record", "policy end date", claim.policyEnd().atZone(zone).toLocalDate(),
                    "incident date", incident, Kind.NOT_BEFORE, List.of(), false, null, List.of(claim.incidentAt()),
                    List.of(EvidenceRef.claimField("incident.incidentAt"), EvidenceRef.claimField("policy.policyEndDate")));
        }
        int claimOnly = col.comparisons.size();

        List<String> notChecked = new ArrayList<>();
        Extracted invoice = document(inputs, DocumentType.FINAL_INVOICE, "Final invoice", notChecked);
        Extracted estimate = document(inputs, DocumentType.GARAGE_ESTIMATE, "Garage estimate", notChecked);
        if (invoice != null) {
            documentChecks(col, invoice, "Final invoice", incident, claim.incidentAt());
        }
        if (estimate != null) {
            documentChecks(col, estimate, "Garage estimate", incident, claim.incidentAt());
        }
        if (invoice != null && estimate != null && invoice.date("documentDate") != null && estimate.date("documentDate") != null) {
            List<String> doubts = union(invoice.doubts(OWN_FINDINGS), estimate.doubts(OWN_FINDINGS));
            col.compare("ESTIMATE_NOT_AFTER_INVOICE", "Final invoice and garage estimate", "invoice date", invoice.date("documentDate"),
                    "estimate date", estimate.date("documentDate"), Kind.NOT_BEFORE, doubts,
                    invoice.formatAssumed("documentDate") || estimate.formatAssumed("documentDate"), invoice.printed("documentDate"), List.of(),
                    List.of(invoice.ref("documentDate"), estimate.ref("documentDate")));
        }

        boolean anyInconsistent = col.inconsistent > 0;
        Map<String, Object> values = Results.values();
        values.put("timeZone", zone.getId());
        values.put("toleranceDays", config.dateToleranceDays());
        values.put("comparisonsRun", col.comparisons.size());
        values.put("claimRecordComparisons", claimOnly);
        values.put("inconsistent", col.inconsistent);
        values.put("material", col.material);
        values.put("comparisons", col.comparisons);
        values.put("notChecked", notChecked);

        List<String> doubts = List.copyOf(col.doubts);
        InputReliability reliability = Extracted.reliability(doubts);
        if (anyInconsistent) {
            String first = col.summaries.get(0);
            String more = col.summaries.size() > 1 ? " (" + (col.summaries.size() - 1) + " more)" : "";
            String qualifier = col.material > 0 ? "" : " None of these is treated as material yet because " + col.whyNotMaterial() + ".";
            return Results.flag(DEFINITION, col.material > 0 ? RuleSeverity.REVIEW : RuleSeverity.INFO, reliability, doubts,
                    col.inconsistent + " date discrepanc" + (col.inconsistent == 1 ? "y" : "ies") + " found. " + first + more + qualifier, values, col.evidence);
        }
        if (!notChecked.isEmpty() || col.comparisons.isEmpty()) {
            String what = col.comparisons.isEmpty() ? "No dates were available to compare" : col.comparisons.size() + " date comparison"
                    + (col.comparisons.size() == 1 ? " is" : "s are") + " consistent, but some dates could not be compared";
            return Results.incomplete(DEFINITION, what + (notChecked.isEmpty() ? "." : ": " + String.join("; ", notChecked) + "."),
                    notChecked.isEmpty() ? List.of("DATES") : List.of("DOCUMENT_DATES"), values, col.evidence);
        }
        return Results.pass(DEFINITION, reliability, doubts, col.comparisons.size() + " date comparisons across the claim record and documents are consistent.", values, col.evidence);
    }

    private static Extracted document(RuleInputs inputs, DocumentType type, String label, List<String> notChecked) {
        DocumentFacts doc = Docs.newest(inputs, type);
        if (doc == null) {
            if (Docs.required(inputs, type)) {
                notChecked.add(label + " has not been received");
            }
            return null;
        }
        Extracted x = Extracted.of(doc);
        if (x == null) {
            notChecked.add(label + " has not been read successfully");
        }
        return x;
    }

    private static void documentChecks(Collector col, Extracted x, String label, LocalDate incident, Instant incidentAt) {
        List<String> doubts = x.doubts(OWN_FINDINGS);
        if (x.date("incidentDate") != null) {
            col.compare("INCIDENT_DATE_AGREES", label, "incident date printed on the document", x.date("incidentDate"), "incident date on the claim", incident,
                    Kind.SAME_DAY, doubts, x.formatAssumed("incidentDate"), x.printed("incidentDate"), List.of(incidentAt),
                    List.of(x.ref("incidentDate"), EvidenceRef.claimField("incident.incidentAt")));
        }
        if (x.date("documentDate") != null) {
            col.compare("DOCUMENT_NOT_BEFORE_INCIDENT", label, "document date", x.date("documentDate"), "incident date", incident,
                    Kind.NOT_BEFORE, doubts, x.formatAssumed("documentDate"), x.printed("documentDate"), List.of(incidentAt),
                    List.of(x.ref("documentDate"), EvidenceRef.claimField("incident.incidentAt")));
        }
        if (x.date("repairStartDate") != null) {
            col.compare("REPAIR_START_NOT_BEFORE_INCIDENT", label, "repair start date", x.date("repairStartDate"), "incident date", incident,
                    Kind.NOT_BEFORE, doubts, x.formatAssumed("repairStartDate"), x.printed("repairStartDate"), List.of(incidentAt),
                    List.of(x.ref("repairStartDate"), EvidenceRef.claimField("incident.incidentAt")));
        }
        if (x.date("repairCompletionDate") != null) {
            col.compare("REPAIR_END_NOT_BEFORE_INCIDENT", label, "repair completion date", x.date("repairCompletionDate"), "incident date", incident,
                    Kind.NOT_BEFORE, doubts, x.formatAssumed("repairCompletionDate"), x.printed("repairCompletionDate"), List.of(incidentAt),
                    List.of(x.ref("repairCompletionDate"), EvidenceRef.claimField("incident.incidentAt")));
            if (x.date("repairStartDate") != null) {
                col.compare("REPAIR_END_NOT_BEFORE_START", label, "repair completion date", x.date("repairCompletionDate"), "repair start date", x.date("repairStartDate"),
                        Kind.NOT_BEFORE, doubts, x.formatAssumed("repairCompletionDate") || x.formatAssumed("repairStartDate"), x.printed("repairCompletionDate"), List.of(),
                        List.of(x.ref("repairCompletionDate"), x.ref("repairStartDate")));
            }
        }
    }

    private static List<String> union(List<String> a, List<String> b) {
        TreeSet<String> all = new TreeSet<>(a);
        all.addAll(b);
        return List.copyOf(all);
    }

    /** Collects the comparisons and decides, for each discrepancy, whether anything benign could explain it. */
    private static final class Collector {
        private final RuleProperties config;
        private final ZoneId zone;
        final List<Map<String, Object>> comparisons = new ArrayList<>();
        final List<String> summaries = new ArrayList<>();
        final List<EvidenceRef> evidence = new ArrayList<>();
        final TreeSet<String> doubts = new TreeSet<>();
        final TreeSet<String> whyNot = new TreeSet<>();
        int inconsistent;
        int material;

        Collector(RuleProperties config, ZoneId zone) {
            this.config = config;
            this.zone = zone;
        }

        String whyNotMaterial() {
            return String.join(" and ", whyNot);
        }

        void compare(String id, String subject, String observedLabel, LocalDate observed, String referenceLabel, LocalDate reference, Kind kind,
                     List<String> documentDoubts, boolean formatAssumed, String printed, List<Instant> instants, List<EvidenceRef> refs) {
            long diff = ChronoUnit.DAYS.between(reference, observed);
            boolean consistent = consistentFor(kind, observed, reference);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("check", id);
            entry.put("subject", subject);
            entry.put("observed", observedLabel + " " + observed);
            entry.put("reference", referenceLabel + " " + reference);
            entry.put("differenceDays", diff);
            entry.put("status", consistent ? "CONSISTENT" : "INCONSISTENT");
            if (!consistent) {
                List<String> qualifiers = new ArrayList<>();
                if (formatAssumed) {
                    qualifiers.add("FORMAT_ASSUMED");
                    whyNot.add("a date format had to be assumed");
                }
                LocalDate swapped = swapped(printed);
                if (swapped != null && consistentFor(kind, swapped, reference)) {
                    qualifiers.add("DAY_MONTH_ORDER_AMBIGUOUS");
                    whyNot.add("reading day and month the other way round would reconcile it");
                }
                if (Math.abs(diff) == 1 && instants.stream().anyMatch(this::nearMidnight)) {
                    qualifiers.add("NEAR_MIDNIGHT_BOUNDARY");
                    whyNot.add("the one-day gap is close to midnight and may be a time-zone effect");
                }
                boolean docDoubtful = !documentDoubts.isEmpty();
                if (docDoubtful) {
                    whyNot.add("the document was not read reliably");
                }
                boolean isMaterial = qualifiers.isEmpty() && !docDoubtful;
                entry.put("qualifiers", qualifiers);
                entry.put("material", isMaterial);
                inconsistent++;
                if (isMaterial) {
                    material++;
                }
                doubts.addAll(documentDoubts);
                summaries.add(subject + ": the " + observedLabel + " (" + observed + ") " + (kind == Kind.SAME_DAY ? "differs from" : "is before") + " the " + referenceLabel + " (" + reference + ").");
                evidence.addAll(refs);
            }
            comparisons.add(entry);
        }

        private boolean consistentFor(Kind kind, LocalDate observed, LocalDate reference) {
            long diff = ChronoUnit.DAYS.between(reference, observed);
            return kind == Kind.SAME_DAY ? Math.abs(diff) <= config.dateToleranceDays() : diff >= -config.dateToleranceDays();
        }

        private boolean nearMidnight(Instant instant) {
            LocalTime time = instant.atZone(zone).toLocalTime();
            int minutes = time.toSecondOfDay() / 60;
            return Math.min(minutes, 1440 - minutes) <= config.nearMidnightMinutes();
        }
    }

    /** The date read with day and month swapped, for a purely numeric date like 03/09/2026; null when that is not a different valid date. */
    static LocalDate swapped(String printed) {
        if (printed == null) {
            return null;
        }
        Matcher m = NUMERIC_DATE.matcher(printed);
        if (!m.matches()) {
            return null;
        }
        int first = Integer.parseInt(m.group(1));
        int second = Integer.parseInt(m.group(2));
        int year = Integer.parseInt(m.group(3));
        if (m.group(3).length() == 2) {
            year += 2000;
        }
        if (first == second || first > 12 || second > 31) {
            return null;
        }
        try {
            return LocalDate.of(year, first, second);
        } catch (java.time.DateTimeException e) {
            return null;
        }
    }
}
