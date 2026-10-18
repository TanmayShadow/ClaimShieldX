package com.example.ClaimShieldX.history.csv;

import com.example.ClaimShieldX.history.csv.RowCells.TimePoint;
import com.example.ClaimShieldX.history.model.RowErrorCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

/** Row validation for template version 1 of both files. Pure: no database and no clock. */
public final class RowValidators {
    static final Set<String> CLAIM_TYPES = Set.of("COLLISION", "THEFT", "OTHER");

    private RowValidators() {
    }

    public static Validated<HistoricalClaimData> historicalClaim(List<String> header, List<String> cells, ZoneId zone) {
        if (cells.size() != header.size()) {
            RowCells empty = new RowCells(header, java.util.Collections.nCopies(header.size(), ""), zone);
            empty.error(null, RowErrorCode.WRONG_COLUMN_COUNT, "has " + cells.size() + " cells but the header has " + header.size());
            return new Validated<>(null, empty.errors(), null);
        }
        RowCells row = new RowCells(header, cells, zone);
        String sourceRecordId = row.token("source_record_id", true, 100);
        String externalClaimId = row.token("external_claim_id", true, 100);
        Integer version = row.whole("claim_version", false, 1);
        String vehicleToken = row.token("vehicle_token", true, 255);
        String policyToken = row.token("policy_token", false, 255);
        String claimantToken = row.token("claimant_token", false, 255);
        String garageId = row.token("garage_id", false, 255);
        TimePoint incident = row.dateTime("incident_at", true);
        TimePoint reported = row.dateTime("reported_at", false);
        String claimType = row.oneOf("claim_type", true, CLAIM_TYPES);
        String claimStatus = row.label("claim_status", true);
        String outcomeStatus = row.label("outcome_status", false);
        BigDecimal claimed = row.money("claimed_amount", false);
        BigDecimal approved = row.money("approved_amount", false);
        String currency = row.currency("currency", true);
        String summary = row.text("repair_summary", 1000);
        List<String> codes = row.codes("damage_area_codes");
        TimePoint repaired = row.dateTime("repair_completed_at", false);

        // A date can only be compared with another date by calendar day: a date alone has no time of day.
        if (incident != null && reported != null && reported.dateIn(zone).isBefore(incident.dateIn(zone))) {
            row.error("reported_at", RowErrorCode.DATE_ORDER, "cannot be before incident_at");
        }
        if (incident != null && repaired != null && repaired.dateIn(zone).isBefore(incident.dateIn(zone))) {
            row.error("repair_completed_at", RowErrorCode.DATE_ORDER, "cannot be before incident_at");
        }
        if (row.hasErrors()) {
            return new Validated<>(null, row.errors(), sourceRecordId);
        }
        HistoricalClaimData data = new HistoricalClaimData(sourceRecordId, externalClaimId, version == null ? 1 : version, vehicleToken, policyToken,
                claimantToken, garageId, incident.instant(), incident.precision(), reported == null ? null : reported.instant(), claimType, claimStatus,
                outcomeStatus, claimed, approved, currency, summary, codes, repaired == null ? null : repaired.instant());
        return new Validated<>(data, List.of(), sourceRecordId);
    }

    public static Validated<GarageHistoryData> garageHistory(List<String> header, List<String> cells, ZoneId zone) {
        if (cells.size() != header.size()) {
            RowCells empty = new RowCells(header, java.util.Collections.nCopies(header.size(), ""), zone);
            empty.error(null, RowErrorCode.WRONG_COLUMN_COUNT, "has " + cells.size() + " cells but the header has " + header.size());
            return new Validated<>(null, empty.errors(), null);
        }
        RowCells row = new RowCells(header, cells, zone);
        String sourceRecordId = row.token("source_record_id", true, 100);
        String garageId = row.token("garage_id", true, 255);
        LocalDate start = row.date("reporting_period_start", true);
        LocalDate end = row.date("reporting_period_end", true);
        Integer claimCount = row.whole("claim_count", true, 0);
        BigDecimal claimedTotal = row.money("claimed_total", false);
        BigDecimal approvedTotal = row.money("approved_total", false);
        String currency = row.currency("currency", true);
        Integer variance = row.whole("estimate_invoice_variance_count", false, 0);
        Integer disputed = row.whole("disputed_or_reopened_count", false, 0);
        Integer confirmed = row.whole("confirmed_outcome_count", false, 0);

        if (start != null && end != null && end.isBefore(start)) {
            row.error("reporting_period_end", RowErrorCode.DATE_ORDER, "cannot be before reporting_period_start");
        }
        if (claimCount != null) {
            exceeds(row, "estimate_invoice_variance_count", variance, claimCount);
            exceeds(row, "disputed_or_reopened_count", disputed, claimCount);
            exceeds(row, "confirmed_outcome_count", confirmed, claimCount);
        }
        if (row.hasErrors()) {
            return new Validated<>(null, row.errors(), sourceRecordId);
        }
        return new Validated<>(new GarageHistoryData(sourceRecordId, garageId, start, end, claimCount, claimedTotal, approvedTotal, currency, variance,
                disputed, confirmed), List.of(), sourceRecordId);
    }

    private static void exceeds(RowCells row, String column, Integer value, int claimCount) {
        if (value != null && value > claimCount) {
            row.error(column, RowErrorCode.COUNT_EXCEEDS_CLAIM_COUNT, "cannot be more than claim_count");
        }
    }
}
