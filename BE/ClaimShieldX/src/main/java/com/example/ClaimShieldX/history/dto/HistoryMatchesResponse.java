package com.example.ClaimShieldX.history.dto;

import com.example.ClaimShieldX.history.model.ImportStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Earlier claims in the imported history that relate to a claim, with the reason, the matched fields, the method, the source
 * record and any ambiguity of each. Each earlier claim appears once however many identifiers it shares, so the same
 * relationship is never counted twice. Nothing here is a finding: a relationship is something to check.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record HistoryMatchesResponse(
        UUID claimId,
        MatchStatus status,
        DataAvailability dataAvailability,
        int totalPriorClaims,
        boolean truncated,
        int excludedAsSameClaim,
        List<HistoricalMatch> matches,
        GarageContext garage,
        List<String> notes
) {
    public record DataAvailability(long historicalClaimRows, long garageHistoryRows, ImportSummary latestClaimsImport, boolean latestClaimsImportFailed) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ImportSummary(UUID id, ImportStatus status, Instant finishedAt) {
    }

    public enum Relation { SAME_VEHICLE, SAME_POLICY, SAME_CLAIMANT }

    /** EXACT: the vehicle token is identical. CONTEXT: only a weaker token matched (a policy can cover several vehicles). */
    public enum Strength { EXACT, CONTEXT }

    public enum Timing { EARLIER, SAME_DAY, LATER }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SourceRef(UUID importId, int rowNumber, String sourceRecordId, Instant importedAt) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record HistoricalMatch(
            String externalClaimId,
            int claimVersion,
            int versionsMatched,
            Relation relation,
            Strength strength,
            List<String> matchedFields,
            String matchMethod,
            Timing timing,
            long daysBetween,
            String claimType,
            String claimStatus,
            String outcomeStatus,
            String garageId,
            List<String> damageAreaCodes,
            Instant incidentAt,
            String incidentAtPrecision,
            List<String> ambiguities,
            SourceRef source,
            String explanation
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record GarageRecord(String sourceRecordId, LocalDate periodStart, LocalDate periodEnd, int claimCount, BigDecimal claimedTotal,
                               BigDecimal approvedTotal, String currency, Integer estimateInvoiceVarianceCount, Integer disputedOrReopenedCount,
                               Integer confirmedOutcomeCount, SourceRef source) {
    }

    public record GarageContext(String garageId, long importedHistoricalClaims, List<GarageRecord> records, String caution) {
    }
}
