package com.example.ClaimShieldX.history.service;

import com.example.ClaimShieldX.history.dto.HistoryMatchesResponse.*;
import com.example.ClaimShieldX.history.model.HistoricalClaim;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Turns candidate rows into explained matches. Pure: no database, no clock. The rules, all deterministic:
 * <ul>
 * <li>Tokens are compared exactly (case-sensitive, trimmed on import). Nothing fuzzy, nothing inferred.</li>
 * <li>One entry per earlier claim: rows that share several identifiers with the claim, or several versions of one external
 * claim, are merged, so the same relationship is never counted twice. The latest matching version is shown.</li>
 * <li>Only an identical vehicle token is a strong (EXACT) relationship. A shared policy or claimant token alone is CONTEXT.
 * A shared garage never makes a match: it is added as a matched field only when a token already matched.</li>
 * <li>The claim itself (same external id as its internal id) is excluded.</li>
 * </ul>
 */
final class HistoryMatcher {
    static final String METHOD = "EXACT_TOKEN";

    /** The facts of the claim being looked up. */
    record ClaimKeys(String internalId, String vehicleToken, String policyToken, String claimantToken, String garageId, Instant incidentAt) {
    }

    record Matched(List<HistoricalMatch> matches, int totalPriorClaims, boolean truncated, int excludedAsSameClaim) {
    }

    private HistoryMatcher() {
    }

    static Matched match(ClaimKeys claim, Collection<HistoricalClaim> candidates, int maxMatches, ZoneId zone) {
        // One row can come back from several token queries; consider each row once.
        Map<UUID, HistoricalClaim> distinct = new LinkedHashMap<>();
        candidates.forEach(row -> distinct.putIfAbsent(row.getId(), row));

        Map<String, List<Candidate>> byExternalId = new LinkedHashMap<>();
        Set<String> excluded = new TreeSet<>();
        for (HistoricalClaim row : distinct.values()) {
            List<String> fields = matchedFields(claim, row);
            if (fields.isEmpty()) {
                continue;
            }
            if (row.getExternalClaimId().equals(claim.internalId())) {
                excluded.add(row.getExternalClaimId());
                continue;
            }
            byExternalId.computeIfAbsent(row.getExternalClaimId(), k -> new ArrayList<>()).add(new Candidate(row, fields));
        }

        LocalDate claimDate = claim.incidentAt() == null ? null : claim.incidentAt().atZone(zone).toLocalDate();
        List<HistoricalMatch> all = new ArrayList<>();
        for (List<Candidate> versions : byExternalId.values()) {
            all.add(toMatch(claim, versions, claimDate, zone));
        }
        all.sort(Comparator.comparing((HistoricalMatch m) -> m.strength() == Strength.EXACT ? 0 : 1)
                .thenComparingLong(HistoricalMatch::daysBetween).thenComparing(HistoricalMatch::externalClaimId));
        boolean truncated = all.size() > maxMatches;
        return new Matched(List.copyOf(truncated ? all.subList(0, maxMatches) : all), all.size(), truncated, excluded.size());
    }

    private record Candidate(HistoricalClaim row, List<String> fields) {
    }

    private static List<String> matchedFields(ClaimKeys claim, HistoricalClaim row) {
        List<String> fields = new ArrayList<>();
        if (claim.vehicleToken() != null && claim.vehicleToken().equals(row.getVehicleToken())) {
            fields.add("vehicleToken");
        }
        if (claim.policyToken() != null && claim.policyToken().equals(row.getPolicyToken())) {
            fields.add("policyToken");
        }
        if (claim.claimantToken() != null && claim.claimantToken().equals(row.getClaimantToken())) {
            fields.add("claimantToken");
        }
        // A shared garage is context for a relationship that already exists; on its own it is not a relationship.
        if (!fields.isEmpty() && claim.garageId() != null && claim.garageId().equals(row.getGarageId())) {
            fields.add("garageId");
        }
        return fields;
    }

    private static HistoricalMatch toMatch(ClaimKeys claim, List<Candidate> versions, LocalDate claimDate, ZoneId zone) {
        Candidate latest = versions.stream().max(Comparator.comparingInt(c -> c.row().getClaimVersion())).orElseThrow();
        HistoricalClaim row = latest.row();
        // Every field any version matched on, so merging versions never hides a matched identifier.
        LinkedHashSet<String> fields = new LinkedHashSet<>();
        versions.forEach(c -> fields.addAll(c.fields()));
        List<String> matchedFields = List.copyOf(fields);

        Relation relation = fields.contains("vehicleToken") ? Relation.SAME_VEHICLE : fields.contains("policyToken") ? Relation.SAME_POLICY : Relation.SAME_CLAIMANT;
        Strength strength = relation == Relation.SAME_VEHICLE ? Strength.EXACT : Strength.CONTEXT;

        LocalDate historyDate = row.getIncidentAt().atZone(zone).toLocalDate();
        long days = claimDate == null ? 0 : Math.abs(ChronoUnit.DAYS.between(historyDate, claimDate));
        Timing timing = claimDate == null || historyDate.equals(claimDate) ? Timing.SAME_DAY : historyDate.isBefore(claimDate) ? Timing.EARLIER : Timing.LATER;

        List<String> ambiguities = new ArrayList<>();
        if (versions.size() > 1) {
            ambiguities.add("MULTIPLE_VERSIONS");
        }
        if (timing == Timing.SAME_DAY) {
            ambiguities.add("ORDER_UNKNOWN_SAME_DAY");
        }
        if (strength == Strength.CONTEXT) {
            ambiguities.add("WEAK_TOKEN_ONLY");
        }
        if (row.getOutcomeStatus() == null) {
            ambiguities.add("OUTCOME_NOT_RECORDED");
        }

        SourceRef source = new SourceRef(row.getImportId(), row.getRowNumber(), row.getSourceRecordId(), row.getCreatedAt());
        return new HistoricalMatch(row.getExternalClaimId(), row.getClaimVersion(), versions.size(), relation, strength, matchedFields, METHOD, timing, days,
                row.getClaimType(), row.getClaimStatus(), row.getOutcomeStatus(), row.getGarageId(), List.copyOf(row.getDamageAreaCodes()),
                row.getIncidentAt(), row.getIncidentAtPrecision(), List.copyOf(ambiguities), source, explain(relation, timing, days, row.getExternalClaimId()));
    }

    private static String explain(Relation relation, Timing timing, long days, String externalClaimId) {
        String when = switch (timing) {
            case EARLIER -> days + " day(s) before this claim's incident date";
            case LATER -> days + " day(s) after this claim's incident date";
            case SAME_DAY -> "on the same day as this claim's incident date";
        };
        return switch (relation) {
            case SAME_VEHICLE -> "Another claim (insurer id " + externalClaimId + ") in the imported history has the same vehicle token and is dated " + when
                    + ". A repeat on one vehicle is something to check against the evidence, not a finding by itself: the earlier damage may have been repaired or may differ.";
            case SAME_POLICY -> "Another claim (insurer id " + externalClaimId + ") has the same policy token only and is dated " + when
                    + ". A policy can cover several vehicles and a renewal usually changes its token, so this is weak context.";
            case SAME_CLAIMANT -> "Another claim (insurer id " + externalClaimId + ") has the same claimant token only and is dated " + when
                    + ". Matching people is privacy-sensitive and one shared token is weak context.";
        };
    }
}
