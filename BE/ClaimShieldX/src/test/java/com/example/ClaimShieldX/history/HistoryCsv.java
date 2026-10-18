package com.example.ClaimShieldX.history;

import java.util.ArrayList;
import java.util.List;

/** Builds the CSV text of history files for tests, one changed cell at a time. Everything in it is made up. */
public final class HistoryCsv {
    public static final String CLAIMS_HEADER = "source_record_id,external_claim_id,claim_version,vehicle_token,policy_token,claimant_token,garage_id,"
            + "incident_at,reported_at,claim_type,claim_status,outcome_status,claimed_amount,approved_amount,currency,repair_summary,damage_area_codes,repair_completed_at";
    public static final String GARAGE_HEADER = "source_record_id,garage_id,reporting_period_start,reporting_period_end,claim_count,claimed_total,approved_total,"
            + "currency,estimate_invoice_variance_count,disputed_or_reopened_count,confirmed_outcome_count";

    private HistoryCsv() {
    }

    /** One claims row; every field has a valid default and can be replaced. A null cell is written empty. */
    public static final class ClaimRow {
        public String sourceRecordId;
        public String externalClaimId;
        public String version = "1";
        public String vehicle = "VEH-TOKEN-0001";
        public String policy = "POL-TOKEN-0001";
        public String claimant = null;
        public String garage = "GARAGE-DEMO-01";
        public String incidentAt = "2025-03-14T11:00:00+05:30";
        public String reportedAt = "2025-03-15";
        public String type = "COLLISION";
        public String status = "CLOSED";
        public String outcome = null;
        public String claimed = "38500.00";
        public String approved = "36000.00";
        public String currency = "INR";
        public String summary = "Front bumper replaced";
        public String codes = "FRONT_BUMPER|BONNET";
        public String repairedAt = "2025-03-28";

        ClaimRow(String id) {
            this.sourceRecordId = "HC-" + id;
            this.externalClaimId = "CLM-HIST-" + id;
        }

        public ClaimRow vehicle(String value) {
            vehicle = value;
            return this;
        }

        public ClaimRow policy(String value) {
            policy = value;
            return this;
        }

        public ClaimRow claimant(String value) {
            claimant = value;
            return this;
        }

        public ClaimRow garage(String value) {
            garage = value;
            return this;
        }

        public ClaimRow incident(String value) {
            incidentAt = value;
            return this;
        }

        public ClaimRow reported(String value) {
            reportedAt = value;
            return this;
        }

        public ClaimRow external(String value) {
            externalClaimId = value;
            return this;
        }

        public ClaimRow source(String value) {
            sourceRecordId = value;
            return this;
        }

        public ClaimRow version(String value) {
            version = value;
            return this;
        }

        public ClaimRow type(String value) {
            type = value;
            return this;
        }

        public ClaimRow status(String value) {
            status = value;
            return this;
        }

        public ClaimRow outcome(String value) {
            outcome = value;
            return this;
        }

        public ClaimRow claimed(String value) {
            claimed = value;
            return this;
        }

        public ClaimRow approved(String value) {
            approved = value;
            return this;
        }

        public ClaimRow currency(String value) {
            currency = value;
            return this;
        }

        public ClaimRow summary(String value) {
            summary = value;
            return this;
        }

        public ClaimRow codes(String value) {
            codes = value;
            return this;
        }

        public ClaimRow repaired(String value) {
            repairedAt = value;
            return this;
        }

        /** A claim that happened on this date (no time), reported the next day and repaired ten days later: dates that are always consistent. */
        public ClaimRow on(String isoDate) {
            java.time.LocalDate date = java.time.LocalDate.parse(isoDate);
            incidentAt = isoDate;
            reportedAt = date.plusDays(1).toString();
            repairedAt = date.plusDays(10).toString();
            return this;
        }

        /** Like {@link #on}, but with a time of day and an offset. */
        public ClaimRow onAt(String isoDateTime) {
            java.time.LocalDate date = java.time.OffsetDateTime.parse(isoDateTime).toLocalDate();
            incidentAt = isoDateTime;
            reportedAt = date.plusDays(1).toString();
            repairedAt = date.plusDays(10).toString();
            return this;
        }

        public String line() {
            return String.join(",", cell(sourceRecordId), cell(externalClaimId), cell(version), cell(vehicle), cell(policy), cell(claimant), cell(garage),
                    cell(incidentAt), cell(reportedAt), cell(type), cell(status), cell(outcome), cell(claimed), cell(approved), cell(currency), cell(summary),
                    cell(codes), cell(repairedAt));
        }
    }

    public static final class GarageRow {
        public String sourceRecordId;
        public String garage = "GARAGE-DEMO-01";
        public String start = "2025-01-01";
        public String end = "2025-06-30";
        public String claimCount = "120";
        public String claimed = "5400000.00";
        public String approved = "5100000.00";
        public String currency = "INR";
        public String variance = "9";
        public String disputed = "4";
        public String confirmed = "0";

        GarageRow(String id) {
            this.sourceRecordId = "GH-" + id;
        }

        public GarageRow garage(String value) {
            garage = value;
            return this;
        }

        public GarageRow period(String from, String to) {
            start = from;
            end = to;
            return this;
        }

        public GarageRow count(String value) {
            claimCount = value;
            return this;
        }

        public GarageRow totals(String claimedTotal, String approvedTotal) {
            claimed = claimedTotal;
            approved = approvedTotal;
            return this;
        }

        public GarageRow variance(String value) {
            variance = value;
            return this;
        }

        public GarageRow disputed(String value) {
            disputed = value;
            return this;
        }

        public GarageRow confirmed(String value) {
            confirmed = value;
            return this;
        }

        public GarageRow source(String value) {
            sourceRecordId = value;
            return this;
        }

        public String line() {
            return String.join(",", cell(sourceRecordId), cell(garage), cell(start), cell(end), cell(claimCount), cell(claimed), cell(approved), cell(currency),
                    cell(variance), cell(disputed), cell(confirmed));
        }
    }

    public static ClaimRow claim(String id) {
        return new ClaimRow(id);
    }

    public static GarageRow garage(String id) {
        return new GarageRow(id);
    }

    public static String claims(ClaimRow... rows) {
        List<String> lines = new ArrayList<>();
        lines.add(CLAIMS_HEADER);
        for (ClaimRow row : rows) {
            lines.add(row.line());
        }
        return String.join("\r\n", lines) + "\r\n";
    }

    public static String garages(GarageRow... rows) {
        List<String> lines = new ArrayList<>();
        lines.add(GARAGE_HEADER);
        for (GarageRow row : rows) {
            lines.add(row.line());
        }
        return String.join("\r\n", lines) + "\r\n";
    }

    /** Writes one cell, quoted when it contains a comma, a quote or a line break. */
    static String cell(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
