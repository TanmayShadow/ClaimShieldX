package com.example.ClaimShieldX.history;

import com.example.ClaimShieldX.support.TestActors;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.example.ClaimShieldX.history.HistoryCsv.claim;
import static com.example.ClaimShieldX.history.HistoryCsv.claims;
import static com.example.ClaimShieldX.history.HistoryCsv.garage;
import static com.example.ClaimShieldX.history.HistoryCsv.garages;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Repeating an import, conflicting records, versions, overlapping garage periods, two organizations, and two imports at once. */
class HistoryImportIntegrityTests extends HistoryApiTestBase {
    private String rowStatuses(TestActors actor, UUID importId) throws Exception {
        String body = getRows(actor, importId, "size", "100").andReturn().getResponse().getContentAsString();
        return String.join(",", JsonPath.<List<String>>read(body, "$.items[*].status"));
    }

    private String rowErrorCodes(TestActors actor, UUID importId) throws Exception {
        String body = getRows(actor, importId, "status", "REJECTED").andReturn().getResponse().getContentAsString();
        return String.join(",", JsonPath.<List<String>>read(body, "$.items[*].errors[0].code"));
    }

    // ------------------------------------------------------------ the Idempotency-Key

    @Test
    void theSameKeyWithTheSameFileReturnsTheSameImportAndStoresNothingNew() throws Exception {
        TestActors admin = admin();
        byte[] file = claims(claim("1"), claim("2").vehicle("VEH-2")).getBytes(StandardCharsets.UTF_8);
        String key = newKey();

        String first = importBytes(admin, file, "HISTORICAL_CLAIMS", "1", key, null).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        importBytes(admin, file, "HISTORICAL_CLAIMS", "1", key, null).andExpect(status().isAccepted()).andExpect(header().string("Idempotent-Replay", "true"))
                .andExpect(jsonPath("$.id").value((String) JsonPath.read(first, "$.id"))).andExpect(jsonPath("$.counts.accepted").value(2));

        assertThat(count("historical_imports", admin.organizationId())).isEqualTo(1);
        assertThat(count("historical_claims", admin.organizationId())).isEqualTo(2);
        assertThat(count("historical_import_rows", admin.organizationId())).isEqualTo(2);
    }

    @Test
    void theSameKeyWithAnythingDifferentIsRefused() throws Exception {
        TestActors admin = admin();
        byte[] file = claims(claim("1")).getBytes(StandardCharsets.UTF_8);
        byte[] other = claims(claim("2")).getBytes(StandardCharsets.UTF_8);
        String key = newKey();
        importBytes(admin, file, "HISTORICAL_CLAIMS", "1", key, "SOURCE-A").andExpect(status().isAccepted());

        importBytes(admin, other, "HISTORICAL_CLAIMS", "1", key, "SOURCE-A").andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        importBytes(admin, file, "GARAGE_HISTORY", "1", key, "SOURCE-A").andExpect(status().isUnprocessableEntity());
        importBytes(admin, file, "HISTORICAL_CLAIMS", "1", key, "SOURCE-B").andExpect(status().isUnprocessableEntity());
        importBytes(admin, file, "HISTORICAL_CLAIMS", "1", key, null).andExpect(status().isUnprocessableEntity());

        assertThat(count("historical_imports", admin.organizationId())).isEqualTo(1);
        assertThat(count("historical_claims", admin.organizationId())).isEqualTo(1);
    }

    @Test
    void aKeyIsScopedToTheOrganization() throws Exception {
        TestActors one = admin();
        TestActors two = admin();
        byte[] file = claims(claim("1")).getBytes(StandardCharsets.UTF_8);
        String key = newKey();

        importBytes(one, file, "HISTORICAL_CLAIMS", "1", key, null).andExpect(status().isAccepted()).andExpect(header().doesNotExist("Idempotent-Replay"));
        importBytes(two, file, "HISTORICAL_CLAIMS", "1", key, null).andExpect(status().isAccepted()).andExpect(header().doesNotExist("Idempotent-Replay"))
                .andExpect(jsonPath("$.counts.accepted").value(1));

        assertThat(count("historical_claims", two.organizationId())).isEqualTo(1);
    }

    // ------------------------------------------------------------ repeating an import

    @Test
    void sendingTheSameFileAgainUnderANewKeySkipsEveryRowAndAddsNothing() throws Exception {
        TestActors admin = admin();
        String csv = claims(claim("1"), claim("2").vehicle("VEH-2"), claim("3").vehicle("VEH-3"));
        importClaimsOk(admin, csv);

        UUID again = importClaimsOk(admin, csv);

        getImport(admin, again).andExpect(jsonPath("$.status").value("COMPLETED")).andExpect(jsonPath("$.counts.totalRows").value(3))
                .andExpect(jsonPath("$.counts.accepted").value(0)).andExpect(jsonPath("$.counts.skippedDuplicate").value(3)).andExpect(jsonPath("$.counts.rejected").value(0));
        assertThat(rowStatuses(admin, again)).isEqualTo("SKIPPED_DUPLICATE,SKIPPED_DUPLICATE,SKIPPED_DUPLICATE");
        assertThat(count("historical_claims", admin.organizationId())).isEqualTo(3);
        assertThat(count("historical_imports", admin.organizationId())).isEqualTo(2);
    }

    @Test
    void aFileWithOldAndNewRowsOnlyAddsTheNewOnes() throws Exception {
        TestActors admin = admin();
        importClaimsOk(admin, claims(claim("1"), claim("2").vehicle("VEH-2")));

        UUID second = importClaimsOk(admin, claims(claim("1"), claim("2").vehicle("VEH-2"), claim("3").vehicle("VEH-3"), claim("4").vehicle("VEH-4")));

        getImport(admin, second).andExpect(jsonPath("$.counts.accepted").value(2)).andExpect(jsonPath("$.counts.skippedDuplicate").value(2));
        assertThat(rowStatuses(admin, second)).isEqualTo("SKIPPED_DUPLICATE,SKIPPED_DUPLICATE,ACCEPTED,ACCEPTED");
        assertThat(count("historical_claims", admin.organizationId())).isEqualTo(4);
    }

    // ------------------------------------------------------------ conflicting and duplicate records

    @Test
    void aRowRepeatedInsideOneFileIsSkippedWhenIdenticalAndRejectedWhenItDiffers() throws Exception {
        TestActors admin = admin();
        String csv = claims(claim("1"), claim("1"), claim("1").claimed("99.00"));

        UUID id = importClaimsOk(admin, csv);

        getImport(admin, id).andExpect(jsonPath("$.status").value("COMPLETED_WITH_ERRORS")).andExpect(jsonPath("$.counts.accepted").value(1))
                .andExpect(jsonPath("$.counts.skippedDuplicate").value(1)).andExpect(jsonPath("$.counts.rejected").value(1));
        assertThat(rowStatuses(admin, id)).isEqualTo("ACCEPTED,SKIPPED_DUPLICATE,REJECTED");
        assertThat(rowErrorCodes(admin, id)).isEqualTo("CONFLICTING_DUPLICATE");
        assertThat(count("historical_claims", admin.organizationId())).isEqualTo(1);
    }

    @Test
    void aRecordThatArrivesAgainWithDifferentContentIsRejectedAndNeverOverwrites() throws Exception {
        TestActors admin = admin();
        importClaimsOk(admin, claims(claim("1").claimed("100.00")));

        UUID second = importClaimsOk(admin, claims(claim("1").claimed("999.00")));

        getImport(admin, second).andExpect(jsonPath("$.status").value("COMPLETED_WITH_ERRORS")).andExpect(jsonPath("$.counts.rejected").value(1));
        assertThat(rowErrorCodes(admin, second)).isEqualTo("CONFLICTING_DUPLICATE");
        assertThat(jdbc.queryForObject("select claimed_amount from historical_claims where source_record_id = 'HC-1' and organization_id = ?", java.math.BigDecimal.class, admin.organizationId()))
                .isEqualByComparingTo("100.00");
    }

    @Test
    void theSameClaimVersionUnderAnotherRecordIdIsRejectedButANewVersionIsAccepted() throws Exception {
        TestActors admin = admin();
        importClaimsOk(admin, claims(claim("1")));

        UUID second = importClaimsOk(admin, claims(
                claim("2").external("CLM-HIST-1").version("1"),
                claim("3").external("CLM-HIST-1").version("2").status("REOPENED")));

        assertThat(rowStatuses(admin, second)).isEqualTo("REJECTED,ACCEPTED");
        assertThat(rowErrorCodes(admin, second)).isEqualTo("DUPLICATE_CLAIM_VERSION");
        assertThat(count("historical_claims", admin.organizationId())).isEqualTo(2);
    }

    @Test
    void twoVersionsOfOneClaimInTheSameFileAreBothKeptAndTheSameVersionTwiceIsNot() throws Exception {
        TestActors admin = admin();

        UUID id = importClaimsOk(admin, claims(
                claim("1").external("CLM-X").version("1"),
                claim("2").external("CLM-X").version("2"),
                claim("3").external("CLM-X").version("2")));

        assertThat(rowStatuses(admin, id)).isEqualTo("ACCEPTED,ACCEPTED,REJECTED");
        assertThat(rowErrorCodes(admin, id)).isEqualTo("DUPLICATE_CLAIM_VERSION");
    }

    @Test
    void claimsAndGarageRecordsMayShareARecordIdBecauseTheyAreDifferentRecords() throws Exception {
        TestActors admin = admin();

        importClaimsOk(admin, claims(claim("1").source("SAME-ID")));
        UUID garageImport = importGaragesOk(admin, garages(garage("1").source("SAME-ID")));

        getImport(admin, garageImport).andExpect(jsonPath("$.counts.accepted").value(1));
    }

    // ------------------------------------------------------------ garage periods

    @Test
    void overlappingPeriodsOfOneGarageAreRejectedAndNeighbouringOnesAreNot() throws Exception {
        TestActors admin = admin();
        importGaragesOk(admin, garages(garage("1").period("2025-01-01", "2025-06-30")));

        UUID id = importGaragesOk(admin, garages(
                garage("2").period("2025-06-30", "2025-09-30"),       // shares the last day: overlaps
                garage("3").period("2025-07-01", "2025-09-30"),       // starts the next day: fine
                garage("4").period("2025-08-01", "2025-08-31"),       // inside the previous accepted one: overlaps
                garage("5").period("2024-01-01", "2024-12-31"),       // before: fine
                garage("6").period("2025-03-01", "2025-03-31").garage("GARAGE-DEMO-02"))); // another garage: fine

        assertThat(rowStatuses(admin, id)).isEqualTo("REJECTED,ACCEPTED,REJECTED,ACCEPTED,ACCEPTED");
        assertThat(rowErrorCodes(admin, id)).isEqualTo("OVERLAPPING_PERIOD,OVERLAPPING_PERIOD");
        assertThat(count("garage_history_records", admin.organizationId())).isEqualTo(4);
    }

    @Test
    void garageRowsAreRecognisedWhenSentAgainAndConflictsAreRejected() throws Exception {
        TestActors admin = admin();
        String csv = garages(garage("1"), garage("2").garage("GARAGE-DEMO-02"));
        importGaragesOk(admin, csv);

        UUID again = importGaragesOk(admin, csv);
        UUID changed = importGaragesOk(admin, garages(garage("1").count("121")));

        getImport(admin, again).andExpect(jsonPath("$.counts.skippedDuplicate").value(2));
        assertThat(rowErrorCodes(admin, changed)).isEqualTo("CONFLICTING_DUPLICATE");
        assertThat(count("garage_history_records", admin.organizationId())).isEqualTo(2);
    }

    @Test
    void theSamePeriodOfTheSameGarageInAnotherOrganizationIsFine() throws Exception {
        TestActors one = admin();
        TestActors two = admin();

        importGaragesOk(one, garages(garage("1")));
        UUID other = importGaragesOk(two, garages(garage("1")));

        getImport(two, other).andExpect(jsonPath("$.counts.accepted").value(1));
    }

    // ------------------------------------------------------------ tenancy

    @Test
    void twoOrganizationsMayLoadTheSameFileAndNeitherSeesTheOthersRows() throws Exception {
        TestActors one = admin();
        TestActors two = admin();
        String csv = claims(claim("1"), claim("2").vehicle("VEH-2"));

        UUID first = importClaimsOk(one, csv);
        UUID second = importClaimsOk(two, csv);

        getImport(one, first).andExpect(jsonPath("$.counts.accepted").value(2));
        getImport(two, second).andExpect(jsonPath("$.counts.accepted").value(2));
        assertThat(count("historical_claims", one.organizationId())).isEqualTo(2);
        assertThat(count("historical_claims", two.organizationId())).isEqualTo(2);
        getRows(one, second).andExpect(status().isNotFound());
        getRows(two, first).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------ an import left behind by a process that died

    private UUID processingImport(TestActors admin, String type, String minutesAgo) {
        UUID id = UUID.randomUUID();
        UUID user = jdbc.queryForObject("select id from users where email = ?", UUID.class, admin.email());
        jdbc.update("""
                insert into historical_imports(id, organization_id, uploaded_by, import_type, template_version, sha256, size_bytes, status, started_at)
                values (?, ?, ?, ?, '1', ?, 10, 'PROCESSING', now() - (?::int * interval '1 minute'))
                """, id, admin.organizationId(), user, type, "d".repeat(64), minutesAgo);
        return id;
    }

    @Test
    void anImportLeftProcessingLongAgoIsMarkedFailedWhenTheNextOneStartsAndARecentOneIsNot() throws Exception {
        TestActors admin = admin();
        TestActors other = admin();
        UUID stale = processingImport(admin, "HISTORICAL_CLAIMS", "60");
        UUID recent = processingImport(admin, "GARAGE_HISTORY", "2");   // another type: left alone, and recent anyway
        UUID otherOrganization = processingImport(other, "HISTORICAL_CLAIMS", "60");

        importClaimsOk(admin, claims(claim("1")));

        getImport(admin, stale).andExpect(jsonPath("$.status").value("FAILED")).andExpect(jsonPath("$.failureCode").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.failureDetail").value(org.hamcrest.Matchers.containsString("interrupted"))).andExpect(jsonPath("$.finishedAt").isNotEmpty());
        getImport(admin, recent).andExpect(jsonPath("$.status").value("PROCESSING"));
        getImport(other, otherOrganization).andExpect(jsonPath("$.status").value("PROCESSING"));
    }

    @Test
    void aProcessingImportOfTheSameKindThatIsStillRecentIsLeftAlone() throws Exception {
        TestActors admin = admin();
        UUID running = processingImport(admin, "HISTORICAL_CLAIMS", "3");

        importClaimsOk(admin, claims(claim("1")));

        getImport(admin, running).andExpect(jsonPath("$.status").value("PROCESSING"));
    }

    // ------------------------------------------------------------ imports at the same time

    @Test
    void sameFileSentManyTimesAtOnceUnderDifferentKeysStoresEachRecordExactlyOnce() throws Exception {
        TestActors admin = admin();
        String csv = claims(claim("1"), claim("2").vehicle("VEH-2"), claim("3").vehicle("VEH-3"), claim("4").vehicle("VEH-4"));
        ExecutorService pool = Executors.newFixedThreadPool(6);
        try {
            CountDownLatch go = new CountDownLatch(1);
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                results.add(pool.submit(() -> {
                    go.await();
                    return importCsv(admin, csv, "HISTORICAL_CLAIMS").andReturn().getResponse().getStatus();
                }));
            }
            go.countDown();
            for (Future<Integer> result : results) {
                assertThat(result.get(60, TimeUnit.SECONDS)).isEqualTo(202);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(count("historical_claims", admin.organizationId())).isEqualTo(4);
        assertThat(count("historical_imports", admin.organizationId())).isEqualTo(6);
        assertThat(jdbc.queryForObject("select sum(accepted_rows) from historical_imports where organization_id = ?", Integer.class, admin.organizationId())).isEqualTo(4);
        assertThat(jdbc.queryForObject("select sum(skipped_duplicate_rows) from historical_imports where organization_id = ?", Integer.class, admin.organizationId())).isEqualTo(20);
        assertThat(jdbc.queryForObject("select count(*) from historical_imports where organization_id = ? and status = 'COMPLETED'", Integer.class, admin.organizationId())).isEqualTo(6);
    }

    @Test
    void overlappingGarageFilesSentAtTheSameTimeLetOnlyOneThrough() throws Exception {
        TestActors admin = admin();
        List<String> files = List.of(garages(garage("A").period("2025-01-01", "2025-06-30")), garages(garage("B").period("2025-03-01", "2025-09-30")),
                garages(garage("C").period("2025-05-01", "2025-12-31")), garages(garage("D").period("2025-06-01", "2025-06-30")));
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            CountDownLatch go = new CountDownLatch(1);
            List<Future<Integer>> results = new ArrayList<>();
            for (String file : files) {
                results.add(pool.submit(() -> {
                    go.await();
                    return importCsv(admin, file, "GARAGE_HISTORY").andReturn().getResponse().getStatus();
                }));
            }
            go.countDown();
            for (Future<Integer> result : results) {
                assertThat(result.get(60, TimeUnit.SECONDS)).isEqualTo(202);
            }
        } finally {
            pool.shutdownNow();
        }

        // All four periods overlap each other, so exactly one record can exist whichever ran first.
        assertThat(count("garage_history_records", admin.organizationId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select sum(accepted_rows) from historical_imports where organization_id = ?", Integer.class, admin.organizationId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select sum(rejected_rows) from historical_imports where organization_id = ?", Integer.class, admin.organizationId())).isEqualTo(3);
    }
}
