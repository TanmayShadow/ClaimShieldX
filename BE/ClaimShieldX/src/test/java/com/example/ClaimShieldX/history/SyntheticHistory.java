package com.example.ClaimShieldX.history;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static com.example.ClaimShieldX.history.HistoryCsv.claim;
import static com.example.ClaimShieldX.history.HistoryCsv.garage;

/**
 * A repeatable synthetic history for the demo and the tests (PRD section 17): about 200 prior claims over 70-plus vehicles and
 * 20 garages, plus garage-history figures, with designed scenarios planted among ordinary background claims. Everything is made
 * up: tokens such as VEH-SYN-17, no plates, names, phone numbers or addresses. The same seed always gives the same files.
 *
 * <p>The demo claims these scenarios are meant to be looked up with are in {@link DemoClaim}; their incident date is
 * {@link #DEMO_INCIDENT}. Which scenario a file row belongs to is NOT in the files, so nothing about the expected result can
 * reach a model through the data.
 */
public final class SyntheticHistory {
    /** 10:30 India time on 10 September 2026, the date used for the PRD's example claim. */
    public static final Instant DEMO_INCIDENT = Instant.parse("2026-09-10T05:00:00Z");
    private static final LocalDate DEMO_DATE = LocalDate.of(2026, 9, 10);

    public record Dataset(String claimsCsv, String garageCsv, int claimRows, int garageRows, Set<String> vehicles, Set<String> garages) {
    }

    /** A claim to look up against the loaded history, with what the scenario is about. */
    public record DemoClaim(String internalId, String vehicle, String policy, String claimant, String garage, String scenario) {
    }

    public static final DemoClaim REPEAT_DAMAGE = new DemoClaim("CLM-DEMO-REPEAT", "VEH-REPEAT-01", "POL-REPEAT-RENEWED", null, "GARAGE-SYN-03", "possible repeat damage on one vehicle");
    public static final DemoClaim BENIGN_GARAGE = new DemoClaim("CLM-DEMO-BENIGN", "VEH-NEW-BENIGN", "POL-NEW-BENIGN", null, "GARAGE-BIG-01", "a large garage with many unrelated claims");
    public static final DemoClaim FLEET = new DemoClaim("CLM-DEMO-FLEET", "VEH-FLEET-01", "POL-FLEET-01", null, "GARAGE-SYN-05", "a fleet vehicle with many claims over time");
    public static final DemoClaim CLEAN = new DemoClaim("CLM-DEMO-CLEAN", "VEH-CLEAN-01", "POL-CLEAN-01", null, "GARAGE-SYN-07", "no history at all for this vehicle");
    public static final DemoClaim FAMILY_POLICY = new DemoClaim("CLM-DEMO-FAMILY", "VEH-NEW-FAMILY", "POL-FAMILY-01", null, "GARAGE-SYN-02", "same policy as claims on other vehicles");
    public static final DemoClaim SHARED_CLAIMANT = new DemoClaim("CLM-DEMO-CLAIMANT", "VEH-NEW-CLAIMANT", "POL-NEW-CLAIMANT", "CLT-SHARED-01", "GARAGE-SYN-04", "same claimant token as claims on other vehicles");
    public static final DemoClaim VERSIONS = new DemoClaim("CLM-DEMO-VERSIONS", "VEH-VERS-01", "POL-VERS-01", null, "GARAGE-SYN-06", "an earlier claim recorded in two versions");
    public static final DemoClaim SUSPICION_NO_OUTCOME = new DemoClaim("CLM-DEMO-UNDECIDED", "VEH-UNDECIDED-01", "POL-UNDECIDED-01", null, "GARAGE-SYN-08", "an earlier claim still under review with no outcome");
    public static final DemoClaim SAME_DAY = new DemoClaim("CLM-DEMO-SAMEDAY", "VEH-SAMEDAY-01", "POL-SAMEDAY-01", null, "GARAGE-SYN-09", "an earlier claim on the same calendar day");

    public static final List<DemoClaim> DEMO_CLAIMS = List.of(REPEAT_DAMAGE, BENIGN_GARAGE, FLEET, CLEAN, FAMILY_POLICY, SHARED_CLAIMANT, VERSIONS, SUSPICION_NO_OUTCOME, SAME_DAY);

    private static final String[] AREAS = {"FRONT_BUMPER", "REAR_BUMPER", "BONNET", "DOOR_LEFT", "DOOR_RIGHT", "WINDSHIELD", "HEADLAMP", "TAIL_LAMP", "ROOF", "FENDER_LEFT", "FENDER_RIGHT"};
    private static final int BACKGROUND_CLAIMS = 150;

    private SyntheticHistory() {
    }

    public static Dataset generate(long seed) {
        Random random = new Random(seed);
        List<HistoryCsv.ClaimRow> rows = new ArrayList<>();
        int next = 1;

        // ---- ordinary background claims: 70 vehicles, 17 normal garages, a mix of types and statuses
        for (int i = 0; i < BACKGROUND_CLAIMS; i++) {
            int vehicle = random.nextInt(70);
            LocalDate date = LocalDate.of(2024, 1, 1).plusDays(random.nextInt(900));
            boolean theft = random.nextInt(100) < 8;
            HistoryCsv.ClaimRow row = claim(String.format("%04d", next++)).external(String.format("CLM-SYN-%04d", i + 1))
                    .vehicle("VEH-SYN-" + String.format("%02d", vehicle)).policy("POL-SYN-" + String.format("%02d", vehicle))
                    .garage(theft ? null : "GARAGE-SYN-" + String.format("%02d", 1 + random.nextInt(17))).on(date.toString())
                    .type(theft ? "THEFT" : random.nextInt(100) < 6 ? "OTHER" : "COLLISION")
                    .status(random.nextInt(100) < 85 ? "CLOSED_PAID" : random.nextInt(2) == 0 ? "CLOSED_NO_PAYMENT" : "OPEN");
            int claimed = 8_000 + random.nextInt(112_000);
            row.claimed(claimed + ".00").approved((claimed * (70 + random.nextInt(31)) / 100) + ".00").summary("Repair of " + area(random).toLowerCase().replace('_', ' ') + " panels");
            row.codes(theft ? null : area(random) + (random.nextBoolean() ? "|" + area(random) : ""));
            if (theft) {
                row.repaired(null).claimed(null).approved(null);
            }
            if (random.nextInt(100) < 8) {
                row.claimant("CLT-SYN-" + String.format("%02d", vehicle));
            }
            if (random.nextInt(100) < 5) {
                row.outcome("NO_ACTION");
            }
            rows.add(row);
        }

        // ---- designed scenarios (their vehicles, policies and claimants appear nowhere in the background)
        rows.add(claim(String.format("%04d", next++)).external("CLM-SYN-REPEAT-A").vehicle(REPEAT_DAMAGE.vehicle()).policy("POL-REPEAT-OLD").garage("GARAGE-SYN-01")
                .on(DEMO_DATE.minusDays(90).toString()).codes("FRONT_BUMPER|BONNET").status("CLOSED_PAID").claimed("62000.00").approved("58000.00").summary("Front bumper and bonnet repaired"));
        rows.add(claim(String.format("%04d", next++)).external("CLM-SYN-REPEAT-B").vehicle(REPEAT_DAMAGE.vehicle()).policy("POL-REPEAT-OLD").garage("GARAGE-SYN-03")
                .on(DEMO_DATE.minusDays(45).toString()).codes("FRONT_BUMPER").status("CLOSED_PAID").claimed("41000.00").approved("40000.00").outcome("NO_ACTION")
                .summary("Front bumper replaced"));

        for (int i = 0; i < 60; i++) { // a large garage with 60 unrelated claims
            rows.add(claim(String.format("%04d", next++)).external(String.format("CLM-SYN-BIG-%02d", i)).vehicle("VEH-BIGGARAGE-" + String.format("%02d", i))
                    .policy("POL-BIGGARAGE-" + String.format("%02d", i)).garage(BENIGN_GARAGE.garage()).on(DEMO_DATE.minusDays(20 + 9 * i).toString())
                    .codes(area(random)).status("CLOSED_PAID").claimed((20_000 + random.nextInt(50_000)) + ".00").approved("30000.00"));
        }

        for (int i = 0; i < 12; i++) { // a fleet vehicle with a claim every couple of months
            rows.add(claim(String.format("%04d", next++)).external(String.format("CLM-SYN-FLEET-%02d", i)).vehicle(FLEET.vehicle()).policy(FLEET.policy())
                    .garage("GARAGE-SYN-05").on(DEMO_DATE.minusDays(25 + 55 * i).toString()).codes(AREAS[i % AREAS.length]).status("CLOSED_PAID").claimed("18000.00").approved("17000.00"));
        }

        rows.add(claim(String.format("%04d", next++)).external("CLM-SYN-FAMILY-A").vehicle("VEH-FAMILY-CAR-1").policy(FAMILY_POLICY.policy()).garage("GARAGE-SYN-02")
                .on(DEMO_DATE.minusDays(200).toString()).status("CLOSED_PAID"));
        rows.add(claim(String.format("%04d", next++)).external("CLM-SYN-FAMILY-B").vehicle("VEH-FAMILY-CAR-2").policy(FAMILY_POLICY.policy()).garage("GARAGE-SYN-02")
                .on(DEMO_DATE.minusDays(120).toString()).status("CLOSED_PAID"));

        rows.add(claim(String.format("%04d", next++)).external("CLM-SYN-CLAIMANT-A").vehicle("VEH-CLAIMANT-CAR-1").policy("POL-CLAIMANT-1").claimant(SHARED_CLAIMANT.claimant())
                .garage("GARAGE-SYN-04").on(DEMO_DATE.minusDays(300).toString()).status("CLOSED_PAID"));
        rows.add(claim(String.format("%04d", next++)).external("CLM-SYN-CLAIMANT-B").vehicle("VEH-CLAIMANT-CAR-2").policy("POL-CLAIMANT-2").claimant(SHARED_CLAIMANT.claimant())
                .garage("GARAGE-SYN-04").on(DEMO_DATE.minusDays(150).toString()).status("CLOSED_PAID"));

        rows.add(claim(String.format("%04d", next++)).external("CLM-SYN-VERSIONS").version("1").vehicle(VERSIONS.vehicle()).policy(VERSIONS.policy()).garage("GARAGE-SYN-06")
                .on(DEMO_DATE.minusDays(70).toString()).status("OPEN").claimed("30000.00").approved(null));
        rows.add(claim(String.format("%04d", next++)).external("CLM-SYN-VERSIONS").version("2").vehicle(VERSIONS.vehicle()).policy(VERSIONS.policy()).garage("GARAGE-SYN-06")
                .on(DEMO_DATE.minusDays(70).toString()).status("CLOSED_PAID").claimed("30000.00").approved("27500.00"));

        rows.add(claim(String.format("%04d", next++)).external("CLM-SYN-UNDECIDED").vehicle(SUSPICION_NO_OUTCOME.vehicle()).policy(SUSPICION_NO_OUTCOME.policy()).garage("GARAGE-SYN-08")
                .on(DEMO_DATE.minusDays(150).toString()).status("UNDER_REVIEW").outcome(null).summary("Earlier claim flagged for a closer look; no result recorded"));

        rows.add(claim(String.format("%04d", next++)).external("CLM-SYN-SAMEDAY").vehicle(SAME_DAY.vehicle()).policy(SAME_DAY.policy()).garage("GARAGE-SYN-09")
                .on(DEMO_DATE.toString()).status("OPEN"));

        // ---- garage history: 20 garages, three periods each
        List<HistoryCsv.GarageRow> garages = new ArrayList<>();
        int g = 1;
        Set<String> garageIds = new TreeSet<>();
        for (int i = 1; i <= 17; i++) {
            garageIds.add("GARAGE-SYN-" + String.format("%02d", i));
        }
        garageIds.add("GARAGE-BIG-01");
        garageIds.add("GARAGE-BIG-02");
        garageIds.add(BENIGN_GARAGE.garage());
        for (String garageId : garageIds) {
            boolean big = garageId.startsWith("GARAGE-BIG");
            String[][] periods = {{"2024-01-01", "2024-12-31"}, {"2025-01-01", "2025-12-31"}, {"2026-01-01", "2026-08-31"}};
            for (String[] period : periods) {
                int claims = (big ? 300 : 20) + random.nextInt(big ? 200 : 40);
                int variance = Math.min(claims, (int) (claims * (big ? 0.06 : 0.08) + random.nextInt(4)));
                long claimedTotal = (long) claims * (30_000 + random.nextInt(30_000)); // an average claim of 30,000 to 60,000
                long approvedTotal = claimedTotal * (88 + random.nextInt(8)) / 100;
                garages.add(garage(String.format("%04d", g++)).garage(garageId).period(period[0], period[1]).count(String.valueOf(claims))
                        .totals(claimedTotal + ".00", approvedTotal + ".00")
                        .variance(String.valueOf(variance)).disputed(String.valueOf(Math.min(claims, random.nextInt(6)))).confirmed("0"));
            }
        }

        Set<String> vehicles = new TreeSet<>();
        for (HistoryCsv.ClaimRow row : rows) {
            vehicles.add(row.vehicle);
        }
        return new Dataset(HistoryCsv.claims(rows.toArray(new HistoryCsv.ClaimRow[0])), HistoryCsv.garages(garages.toArray(new HistoryCsv.GarageRow[0])),
                rows.size(), garages.size(), vehicles, garageIds);
    }

    private static String area(Random random) {
        return AREAS[random.nextInt(AREAS.length)];
    }
}
