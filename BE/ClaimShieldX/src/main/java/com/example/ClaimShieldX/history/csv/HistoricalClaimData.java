package com.example.ClaimShieldX.history.csv;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** One validated, normalized historical claim row. */
public record HistoricalClaimData(
        String sourceRecordId,
        String externalClaimId,
        int claimVersion,
        String vehicleToken,
        String policyToken,
        String claimantToken,
        String garageId,
        Instant incidentAt,
        String incidentAtPrecision,
        Instant reportedAt,
        String claimType,
        String claimStatus,
        String outcomeStatus,
        BigDecimal claimedAmount,
        BigDecimal approvedAmount,
        String currency,
        String repairSummary,
        List<String> damageAreaCodes,
        Instant repairCompletedAt
) {
    /**
     * A fingerprint of everything except the record id, so the same record sent again is recognised as a duplicate and a
     * record that changed is recognised as a conflict.
     */
    public String contentHash() {
        return Hashes.sha256(externalClaimId, String.valueOf(claimVersion), vehicleToken, policyToken, claimantToken, garageId,
                String.valueOf(incidentAt), incidentAtPrecision, String.valueOf(reportedAt), claimType, claimStatus, outcomeStatus,
                claimedAmount == null ? null : claimedAmount.toPlainString(), approvedAmount == null ? null : approvedAmount.toPlainString(),
                currency, repairSummary, String.join("|", damageAreaCodes), String.valueOf(repairCompletedAt));
    }
}
