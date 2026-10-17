package com.example.ClaimShieldX.history.csv;

import com.example.ClaimShieldX.history.model.ImportType;

import java.util.List;
import java.util.Optional;

import static java.util.List.of;

/**
 * Template version 1 of both files. Everything is a pseudonymous token or a plain value: no registration numbers, names or
 * addresses are asked for, and an identifier that is only meaningful to the insurer stays opaque to us. The meaning of
 * {@code claim_status} and {@code outcome_status} belongs to the insurer; this system stores them as labels and never
 * interprets them, because no insurer data dictionary has been agreed yet.
 */
public final class CsvTemplates {
    public static final String VERSION_1 = "1";

    private static final CsvTemplate HISTORICAL_CLAIMS_V1 = new CsvTemplate(ImportType.HISTORICAL_CLAIMS, VERSION_1, of(
            new CsvColumn("source_record_id", true, "Unique id of this record in the source system, up to 100 characters. The key used to recognise a re-imported row."),
            new CsvColumn("external_claim_id", true, "The insurer's id of the claim, up to 100 characters."),
            new CsvColumn("claim_version", false, "Whole number from 1, default 1. One external claim id may appear once per version."),
            new CsvColumn("vehicle_token", true, "Pseudonymous vehicle token, matched exactly (case-sensitive, after trimming) with the vehicle token of a claim."),
            new CsvColumn("policy_token", false, "Pseudonymous policy token. A renewal usually changes it, so it is weak context only."),
            new CsvColumn("claimant_token", false, "Pseudonymous claimant token. Only when necessary: matching people is privacy-sensitive."),
            new CsvColumn("garage_id", false, "Garage identifier, matched exactly with the garage of a claim."),
            new CsvColumn("incident_at", true, "ISO-8601 date-time with offset (2025-03-14T11:00:00+05:30 or ...Z) or a date (2025-03-14)."),
            new CsvColumn("reported_at", false, "Same formats as incident_at. Not before the incident date."),
            new CsvColumn("claim_type", true, "COLLISION, THEFT or OTHER."),
            new CsvColumn("claim_status", true, "The insurer's status as a label (letters, digits, underscore). Stored, never interpreted."),
            new CsvColumn("outcome_status", false, "The outcome as a label, only when actually known and permitted to share. Empty means not known; it never means cleared."),
            new CsvColumn("claimed_amount", false, "Plain decimal, at most 2 decimals, no grouping or currency symbol."),
            new CsvColumn("approved_amount", false, "Plain decimal, at most 2 decimals."),
            new CsvColumn("currency", true, "ISO code, three capital letters, for example INR."),
            new CsvColumn("repair_summary", false, "Free text, up to 1000 characters. Untrusted: treated as data everywhere, including in model prompts."),
            new CsvColumn("damage_area_codes", false, "Up to 20 codes separated by | (capital letters, digits, underscore)."),
            new CsvColumn("repair_completed_at", false, "Same formats as incident_at. Not before the incident date.")),
            "HC-DEMO-0001,CLM-HIST-0001,1,VEH-TOKEN-0001,POL-TOKEN-0001,,GARAGE-DEMO-01,2025-03-14T11:00:00+05:30,2025-03-15,COLLISION,CLOSED,,38500.00,36000.00,INR,Front bumper replaced,FRONT_BUMPER|BONNET,2025-03-28");

    private static final CsvTemplate GARAGE_HISTORY_V1 = new CsvTemplate(ImportType.GARAGE_HISTORY, VERSION_1, of(
            new CsvColumn("source_record_id", true, "Unique id of this record in the source system, up to 100 characters."),
            new CsvColumn("garage_id", true, "Garage identifier, matched exactly with the garage of a claim."),
            new CsvColumn("reporting_period_start", true, "First day covered, as a date (2025-01-01)."),
            new CsvColumn("reporting_period_end", true, "Last day covered, as a date. Not before the first day. Periods of one garage may not overlap."),
            new CsvColumn("claim_count", true, "Whole number of claims in the period."),
            new CsvColumn("claimed_total", false, "Plain decimal, at most 2 decimals."),
            new CsvColumn("approved_total", false, "Plain decimal, at most 2 decimals."),
            new CsvColumn("currency", true, "ISO code, three capital letters, for example INR."),
            new CsvColumn("estimate_invoice_variance_count", false, "Whole number, at most claim_count."),
            new CsvColumn("disputed_or_reopened_count", false, "Whole number, at most claim_count. The insurer defines what counts."),
            new CsvColumn("confirmed_outcome_count", false, "Whole number, at most claim_count. Only when authorized to share.")),
            "GH-DEMO-0001,GARAGE-DEMO-01,2025-01-01,2025-06-30,120,5400000.00,5100000.00,INR,9,4,0");

    private CsvTemplates() {
    }

    public static Optional<CsvTemplate> find(ImportType type, String version) {
        if (!VERSION_1.equals(version)) {
            return Optional.empty();
        }
        return Optional.of(type == ImportType.HISTORICAL_CLAIMS ? HISTORICAL_CLAIMS_V1 : GARAGE_HISTORY_V1);
    }

    public static List<String> supportedVersions() {
        return of(VERSION_1);
    }
}
