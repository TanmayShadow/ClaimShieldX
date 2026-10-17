package com.example.ClaimShieldX.history.csv;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One validated garage-history row: aggregate figures an insurer reports for one garage and period. */
public record GarageHistoryData(
        String sourceRecordId,
        String garageId,
        LocalDate periodStart,
        LocalDate periodEnd,
        int claimCount,
        BigDecimal claimedTotal,
        BigDecimal approvedTotal,
        String currency,
        Integer estimateInvoiceVarianceCount,
        Integer disputedOrReopenedCount,
        Integer confirmedOutcomeCount
) {
    public String contentHash() {
        return Hashes.sha256(garageId, String.valueOf(periodStart), String.valueOf(periodEnd), String.valueOf(claimCount),
                claimedTotal == null ? null : claimedTotal.toPlainString(), approvedTotal == null ? null : approvedTotal.toPlainString(), currency,
                String.valueOf(estimateInvoiceVarianceCount), String.valueOf(disputedOrReopenedCount), String.valueOf(confirmedOutcomeCount));
    }
}
