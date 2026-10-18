package com.example.ClaimShieldX.rules.engine;

import com.example.ClaimShieldX.rules.config.RuleProperties;
import com.example.ClaimShieldX.rules.engine.RuleInputs.HistoryFacts;
import com.example.ClaimShieldX.rules.engine.RuleInputs.HistoryFacts.HistoryMatch;
import com.example.ClaimShieldX.rules.model.EvidenceRef;
import com.example.ClaimShieldX.rules.model.InputReliability;
import com.example.ClaimShieldX.rules.model.RuleDefinition;
import com.example.ClaimShieldX.rules.model.RuleResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Records the relationships found by the history lookup of B6, with the basis for each. The vehicle token is an exact
 * match; policy and claimant tokens are context. A relationship is never proof: fleets, family policies and repaired damage all
 * produce one. It is REVIEW only when an exact (same vehicle) earlier claim falls within the configured window, and a
 * missing or failed import is INCOMPLETE so that "no history was available" is never read as "nothing found".
 */
public final class HistoricalRelationshipRule implements Rule {
    public static final RuleDefinition DEFINITION = new RuleDefinition("HISTORICAL_RELATIONSHIP", "1", "Historical relationship",
            "Records earlier claims in the imported history that share this claim's vehicle (exact), or its policy or claimant (context), with the basis and source of each.",
            List.of("An import of historical claims for the organization"),
            List.of("The earlier damage may have been repaired completely, or may be different damage.", "A fleet, a family policy or a shared claimant token produces links that mean nothing on their own.",
                    "A garage's claim volume or sharing a garage is context only and is not used here."));

    @Override
    public RuleDefinition definition() {
        return DEFINITION;
    }

    @Override
    public RuleResult evaluate(RuleInputs inputs, RuleProperties config) {
        HistoryFacts history = inputs.history();
        Map<String, Object> values = Results.values();
        values.put("lookupStatus", history.status());
        values.put("latestClaimsImportId", history.latestClaimsImportId() == null ? null : history.latestClaimsImportId().toString());
        values.put("latestImportFailed", history.latestImportFailed());
        switch (history.status()) {
            case "NO_HISTORICAL_DATA_AVAILABLE":
                return Results.incomplete(DEFINITION, "No historical claims have been imported, so no comparison with earlier claims was possible. This is not the same as finding no match.",
                        List.of("HISTORICAL_CLAIMS_IMPORT"), values, List.of());
            case "HISTORY_IMPORT_FAILED":
                return Results.incomplete(DEFINITION, "The import of historical claims failed and none are stored, so no comparison with earlier claims was possible.",
                        List.of("HISTORICAL_CLAIMS_IMPORT"), values, List.of());
            default:
                break;
        }
        List<String> doubts = history.latestImportFailed() ? List.of("LATEST_HISTORY_IMPORT_FAILED") : List.of();
        InputReliability reliability = doubts.isEmpty() ? InputReliability.RELIABLE : InputReliability.DOUBTFUL;
        if (history.matches().isEmpty()) {
            return Results.pass(DEFINITION, reliability, doubts, "History exists, and no earlier claim shares this claim's vehicle, policy or claimant token.", values, List.of());
        }

        Map<String, Integer> byRelation = new TreeMap<>();
        boolean material = false;
        List<Map<String, Object>> listed = new ArrayList<>();
        List<EvidenceRef> evidence = new ArrayList<>();
        for (HistoryMatch m : history.matches()) {
            byRelation.merge(m.relation(), 1, Integer::sum);
            boolean exact = "EXACT".equals(m.strength());
            boolean recent = ("SAME_DAY".equals(m.timing()) || "EARLIER".equals(m.timing())) && m.daysBetween() != null && m.daysBetween() <= config.historyRecentDays();
            material |= exact && recent;
            if (listed.size() < config.maxListedMatches()) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("externalClaimId", m.externalClaimId());
                entry.put("claimVersion", m.claimVersion());
                entry.put("relation", m.relation());
                entry.put("strength", m.strength());
                entry.put("timing", m.timing());
                entry.put("daysBetween", m.daysBetween());
                entry.put("claimStatus", m.claimStatus());
                entry.put("outcomeStatus", m.outcomeStatus());
                entry.put("ambiguities", m.ambiguities());
                listed.add(entry);
                evidence.add(EvidenceRef.historicalRecord(m.importId(), m.rowNumber(), m.sourceRecordId()));
            }
        }
        values.put("totalPriorClaims", history.totalPriorClaims());
        values.put("listPossiblyIncomplete", history.truncated());
        values.put("byRelation", byRelation);
        values.put("recentWindowDays", config.historyRecentDays());
        values.put("matches", listed);
        values.put("listedMatches", listed.size());
        long exactCount = history.matches().stream().filter(m -> "EXACT".equals(m.strength())).count();
        String detail = exactCount == 0
                ? "Only policy or claimant tokens are shared, which is context rather than a link between the vehicles."
                : exactCount + " share" + (exactCount == 1 ? "s" : "") + " this claim's vehicle token" + (material ? ", at least one within " + config.historyRecentDays() + " days." : ", none within " + config.historyRecentDays() + " days.");
        return Results.flag(DEFINITION, Results.severity(material, doubts), reliability, doubts,
                (history.totalPriorClaims() == 1 ? "1 earlier claim in the imported history relates" : history.totalPriorClaims() + " earlier claims in the imported history relate") + " to this claim. " + detail
                        + " A relationship is a reason to look at the evidence, not a finding.", values, evidence);
    }
}
