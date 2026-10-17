package com.example.ClaimShieldX.history;

import com.example.ClaimShieldX.support.TestActors;
import com.example.ClaimShieldX.support.TestTokens;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.UUID;

import static com.example.ClaimShieldX.history.HistoryCsv.claim;
import static com.example.ClaimShieldX.history.HistoryCsv.claims;
import static com.example.ClaimShieldX.history.HistoryCsv.garage;
import static com.example.ClaimShieldX.history.HistoryCsv.garages;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The import API: who may use it, what a good file and a partly bad file give back, and what is stored. */
class HistoryImportApiTests extends HistoryApiTestBase {
    // ------------------------------------------------------------ access

    @Test
    void everyEndpointNeedsASignedInUser() throws Exception {
        UUID id = UUID.randomUUID();

        for (String url : List.of("/api/v1/historical-imports", "/api/v1/historical-imports/" + id, "/api/v1/historical-imports/" + id + "/rows",
                "/api/v1/historical-imports/templates/HISTORICAL_CLAIMS")) {
            mockMvc.perform(get(url)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        }
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/historical-imports")
                .file(new org.springframework.mock.web.MockMultipartFile("file", "h.csv", "text/csv", "x".getBytes()))
                .param("importType", "HISTORICAL_CLAIMS").param("templateVersion", "1").header("Idempotency-Key", newKey())).andExpect(status().isUnauthorized());
    }

    @Test
    void anExpiredOrForgedTokenIsRejected() throws Exception {
        TestActors actor = admin();

        mockMvc.perform(get("/api/v1/historical-imports").header("Authorization", "Bearer " + TestTokens.expired(jwtSecret, actor.email()))).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/historical-imports").header("Authorization", "Bearer " + TestTokens.signedWithAnotherKey(actor.email()))).andExpect(status().isUnauthorized());
    }

    @Test
    void onlyAnOrganizationAdministratorMayUseTheImportApi() throws Exception {
        TestActors investigator = investigator();
        UUID id = UUID.randomUUID();

        importCsv(investigator, claims(claim("1")), "HISTORICAL_CLAIMS").andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        for (String url : List.of("/api/v1/historical-imports", "/api/v1/historical-imports/" + id, "/api/v1/historical-imports/" + id + "/rows",
                "/api/v1/historical-imports/templates/GARAGE_HISTORY")) {
            mockMvc.perform(get(url).header("Authorization", investigator.bearer())).andExpect(status().isForbidden());
        }
        assertThat(count("historical_imports", investigator.organizationId())).isZero();
    }

    // ------------------------------------------------------------ request validation

    @Test
    void theRequestItselfIsValidatedBeforeAnyImportIsCreated() throws Exception {
        TestActors admin = admin();
        byte[] file = claims(claim("1")).getBytes(StandardCharsets.UTF_8);

        importBytes(admin, file, "HISTORICAL_CLAIMS", "1", null, null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
        importBytes(admin, file, "HISTORICAL_CLAIMS", "1", "short", null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        importBytes(admin, file, "SOMETHING_ELSE", "1", newKey(), null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        importBytes(admin, file, "HISTORICAL_CLAIMS", "2", newKey(), null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("templateVersion"));
        importBytes(admin, file, "HISTORICAL_CLAIMS", "", newKey(), null).andExpect(status().isBadRequest());
        importBytes(admin, file, "HISTORICAL_CLAIMS", "1", newKey(), "has spaces").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("source"));
        importBytes(admin, file, "HISTORICAL_CLAIMS", "1", newKey(), "<script>").andExpect(status().isBadRequest());
        importBytes(admin, file, "HISTORICAL_CLAIMS", "1", newKey(), "x".repeat(101)).andExpect(status().isBadRequest());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/historical-imports")
                .param("importType", "HISTORICAL_CLAIMS").param("templateVersion", "1").header("Idempotency-Key", newKey()).header("Authorization", admin.bearer()))
                .andExpect(status().isBadRequest());

        assertThat(count("historical_imports", admin.organizationId())).isZero();
    }

    // ------------------------------------------------------------ a good file

    @Test
    void aValidClaimsFileIsImportedAndDescribed() throws Exception {
        TestActors admin = admin();
        String csv = claims(claim("1"), claim("2").vehicle("VEH-TOKEN-0002"), claim("3").vehicle("VEH-TOKEN-0003"));

        MvcResult result = importBytes(admin, csv.getBytes(StandardCharsets.UTF_8), "HISTORICAL_CLAIMS", "1", newKey(), "INSURER_DEMO")
                .andExpect(status().isAccepted())
                .andExpect(header().exists("Location"))
                .andExpect(header().doesNotExist("Idempotent-Replay"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.importType").value("HISTORICAL_CLAIMS"))
                .andExpect(jsonPath("$.templateVersion").value("1"))
                .andExpect(jsonPath("$.source").value("INSURER_DEMO"))
                .andExpect(jsonPath("$.counts.totalRows").value(3))
                .andExpect(jsonPath("$.counts.accepted").value(3))
                .andExpect(jsonPath("$.counts.rejected").value(0))
                .andExpect(jsonPath("$.counts.skippedDuplicate").value(0))
                .andExpect(jsonPath("$.sizeBytes").value(csv.getBytes(StandardCharsets.UTF_8).length))
                .andExpect(jsonPath("$.startedAt").isNotEmpty()).andExpect(jsonPath("$.finishedAt").isNotEmpty()).andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.failureCode").doesNotExist()).andExpect(jsonPath("$.uploadedBy").doesNotExist()).andReturn();

        String body = result.getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");
        assertThat((String) JsonPath.read(body, "$.sha256")).isEqualTo(java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(csv.getBytes(StandardCharsets.UTF_8))));
        assertThat(result.getResponse().getHeader("Location")).isEqualTo("/api/v1/historical-imports/" + id);
        assertThat(count("historical_claims", admin.organizationId())).isEqualTo(3);
        assertThat(count("historical_import_rows", admin.organizationId())).isEqualTo(3);
        getImport(admin, UUID.fromString(id)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void aValidGarageHistoryFileIsImported() throws Exception {
        TestActors admin = admin();
        String csv = garages(garage("1"), garage("2").garage("GARAGE-DEMO-02"), garage("3").period("2025-07-01", "2025-12-31"));

        importCsv(admin, csv, "GARAGE_HISTORY").andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.importType").value("GARAGE_HISTORY")).andExpect(jsonPath("$.counts.accepted").value(3));

        assertThat(count("garage_history_records", admin.organizationId())).isEqualTo(3);
        assertThat(count("historical_claims", admin.organizationId())).isZero();
    }

    @Test
    void aClaimsFileSentAsGarageHistoryIsAHeaderFailureNotASilentMismatch() throws Exception {
        TestActors admin = admin();

        importCsv(admin, claims(claim("1")), "GARAGE_HISTORY").andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureCode").value("INVALID_HEADER"));

        assertThat(count("garage_history_records", admin.organizationId())).isZero();
    }

    // ------------------------------------------------------------ a partly bad file

    private String partlyBad() {
        String bad = claims(claim("1"),
                claim("2").type("FLOOD"),
                claim("3").vehicle("VEH-TOKEN-0003"),
                claim("4").incident("yesterday"),
                claim("5"),
                claim("6").vehicle("VEH-TOKEN-0006")).trim();
        // Row 5 loses a cell: the wrong number of cells.
        String[] lines = bad.split("\r\n");
        lines[5] = lines[5].substring(0, lines[5].lastIndexOf(','));
        return String.join("\r\n", lines) + "\r\n";
    }

    @Test
    void goodRowsAreKeptAndBadRowsAreListedWithTheirReasons() throws Exception {
        TestActors admin = admin();

        UUID id = UUID.fromString(JsonPath.read(importCsv(admin, partlyBad(), "HISTORICAL_CLAIMS").andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("COMPLETED_WITH_ERRORS")).andExpect(jsonPath("$.counts.totalRows").value(6))
                .andExpect(jsonPath("$.counts.accepted").value(3)).andExpect(jsonPath("$.counts.rejected").value(3))
                .andReturn().getResponse().getContentAsString(), "$.id"));

        assertThat(count("historical_claims", admin.organizationId())).isEqualTo(3);
        assertThat(jdbc.queryForList("select source_record_id from historical_claims where organization_id = ? order by source_record_id", String.class, admin.organizationId()))
                .containsExactly("HC-1", "HC-3", "HC-6");

        getRows(admin, id, "status", "REJECTED").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.items[0].rowNumber").value(2)).andExpect(jsonPath("$.items[0].status").value("REJECTED"))
                .andExpect(jsonPath("$.items[0].sourceRecordId").value("HC-2"))
                .andExpect(jsonPath("$.items[0].errors[0].field").value("claim_type")).andExpect(jsonPath("$.items[0].errors[0].code").value("INVALID_VALUE"))
                .andExpect(jsonPath("$.items[1].rowNumber").value(4)).andExpect(jsonPath("$.items[1].errors[0].code").value("INVALID_DATE"))
                .andExpect(jsonPath("$.items[2].rowNumber").value(5)).andExpect(jsonPath("$.items[2].errors[0].code").value("WRONG_COLUMN_COUNT"))
                .andExpect(jsonPath("$.items[2].errors[0].field").doesNotExist());
        getRows(admin, id, "status", "ACCEPTED").andExpect(jsonPath("$.totalElements").value(3)).andExpect(jsonPath("$.items[*].errors").doesNotExist())
                .andExpect(jsonPath("$.items[*].rowNumber", contains(1, 3, 6)));
        getRows(admin, id).andExpect(jsonPath("$.totalElements").value(6)).andExpect(jsonPath("$.items[*].rowNumber", contains(1, 2, 3, 4, 5, 6)));
    }

    @Test
    void theCountsAlwaysAddUpAndEveryRowHasExactlyOneResult() throws Exception {
        TestActors admin = admin();
        UUID id = importClaimsOk(admin, partlyBad());

        var row = jdbc.queryForMap("select total_rows, accepted_rows, rejected_rows, skipped_duplicate_rows from historical_imports where id = ?", id);

        assertThat((Integer) row.get("total_rows")).isEqualTo((Integer) row.get("accepted_rows") + (Integer) row.get("rejected_rows") + (Integer) row.get("skipped_duplicate_rows"));
        assertThat(jdbc.queryForObject("select count(*) from historical_import_rows where import_id = ?", Integer.class, id)).isEqualTo(6);
        assertThat(jdbc.queryForObject("select count(distinct row_number) from historical_import_rows where import_id = ?", Integer.class, id)).isEqualTo(6);
    }

    @Test
    void rowResultsPageAndSortWithTheStandardRules() throws Exception {
        TestActors admin = admin();
        HistoryCsv.ClaimRow[] rows = new HistoryCsv.ClaimRow[25];
        for (int i = 0; i < rows.length; i++) {
            rows[i] = claim(String.format("%02d", i)).vehicle("VEH-" + i);
        }
        UUID id = importClaimsOk(admin, claims(rows));

        getRows(admin, id, "size", "10", "page", "0").andExpect(jsonPath("$.items", hasSize(10))).andExpect(jsonPath("$.totalPages").value(3)).andExpect(jsonPath("$.items[0].rowNumber").value(1));
        getRows(admin, id, "size", "10", "page", "2").andExpect(jsonPath("$.items", hasSize(5))).andExpect(jsonPath("$.items[4].rowNumber").value(25));
        getRows(admin, id, "sort", "rowNumber,desc", "size", "3").andExpect(jsonPath("$.items[*].rowNumber", contains(25, 24, 23)));
        getRows(admin, id, "size", "101").andExpect(status().isBadRequest());
        getRows(admin, id, "sort", "sourceRecordId").andExpect(status().isBadRequest());
        getRows(admin, id, "status", "BOGUS").andExpect(status().isBadRequest());
        getRows(admin, id, "page", "-1").andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------ listing and tenancy

    @Test
    void importsAreListedNewestFirstAndOnlyForTheCallersOrganization() throws Exception {
        TestActors mine = admin();
        TestActors other = admin();
        UUID first = importClaimsOk(mine, claims(claim("1")));
        UUID second = importGaragesOk(mine, garages(garage("1")));
        UUID foreign = importClaimsOk(other, claims(claim("1")));

        mockMvc.perform(get("/api/v1/historical-imports").header("Authorization", mine.bearer())).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.items[0].id").value(second.toString()))
                .andExpect(jsonPath("$.items[1].id").value(first.toString())).andExpect(jsonPath("$.items[*].id", not(hasItem(foreign.toString()))));
        mockMvc.perform(get("/api/v1/historical-imports").param("sort", "createdAt,asc").header("Authorization", mine.bearer()))
                .andExpect(jsonPath("$.items[0].id").value(first.toString()));
        mockMvc.perform(get("/api/v1/historical-imports").param("sort", "sha256").header("Authorization", mine.bearer())).andExpect(status().isBadRequest());
    }

    @Test
    void anotherOrganizationsImportLooksExactlyLikeOneThatDoesNotExist() throws Exception {
        TestActors owner = admin();
        TestActors stranger = admin();
        UUID id = importClaimsOk(owner, claims(claim("1")));

        String foreign = getImport(stranger, id).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND")).andReturn().getResponse().getContentAsString();
        String missing = getImport(stranger, UUID.randomUUID()).andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        getRows(stranger, id).andExpect(status().isNotFound());

        assertThat(foreign.replaceAll("\"timestamp\":\"[^\"]*\"", "")).isEqualTo(missing.replaceAll("\"timestamp\":\"[^\"]*\"", ""));
        mockMvc.perform(get("/api/v1/historical-imports/not-a-uuid").header("Authorization", owner.bearer())).andExpect(status().isBadRequest());
    }

    @Test
    void anotherAdministratorOfTheSameOrganizationSeesTheSameImports() throws Exception {
        TestActors one = admin();
        TestActors two = adminOf(one);
        UUID id = importClaimsOk(one, claims(claim("1")));

        getImport(two, id).andExpect(status().isOk());
        getRows(two, id).andExpect(status().isOk());
    }

    // ------------------------------------------------------------ the template

    @Test
    void theTemplateIsADownloadableCsvThatImportsCleanly() throws Exception {
        TestActors admin = admin();

        for (String type : List.of("HISTORICAL_CLAIMS", "GARAGE_HISTORY")) {
            MvcResult result = mockMvc.perform(get("/api/v1/historical-imports/templates/" + type).header("Authorization", admin.bearer()))
                    .andExpect(status().isOk()).andExpect(content().contentType(new MediaType("text", "csv", StandardCharsets.UTF_8)))
                    .andExpect(header().string("Content-Disposition", containsString("attachment")))
                    .andExpect(header().string("Content-Disposition", containsString(type.toLowerCase().replace('_', '-') + "-template-v1.csv")))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff")).andReturn();
            String csv = result.getResponse().getContentAsString(StandardCharsets.UTF_8);

            assertThat(csv.split("\r\n")[0]).isEqualTo(type.equals("HISTORICAL_CLAIMS") ? HistoryCsv.CLAIMS_HEADER : HistoryCsv.GARAGE_HEADER);
            importCsv(admin, csv, type).andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("COMPLETED")).andExpect(jsonPath("$.counts.accepted").value(1));
        }
        mockMvc.perform(get("/api/v1/historical-imports/templates/HISTORICAL_CLAIMS").param("version", "2").header("Authorization", admin.bearer())).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/historical-imports/templates/NOT_A_TYPE").header("Authorization", admin.bearer())).andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------ what is stored

    @Test
    void valuesAreStoredNormalizedAndUntrustedTextIsCleanedButNeverInterpreted() throws Exception {
        TestActors admin = admin();
        String csv = claims(
                claim("1").vehicle("  VEH-Padded  ").type("collision").status("closed_paid").outcome("no_action").incident("2025-03-14").reported("2025-03-14T18:00:00+05:30")
                        .claimed("38500").approved("0").codes("front_bumper|bonnet|front_bumper").summary("front‮ bumper\u0007 replaced").repaired("2025-03-20"),
                claim("2").vehicle("VEH-2").summary("=HYPERLINK(\"http://example.invalid\")").codes(null).claimed(null).approved(null).repaired(null).reported(null),
                claim("3").vehicle("VEH-3").summary("line one\nline two, with a comma and \"quotes\"").incident("2025-03-14T05:30:00Z"),
                claim("4").vehicle("VEH-4").summary("बम्पर बदला"));

        UUID id = importClaimsOk(admin, csv);

        getImport(admin, id).andExpect(jsonPath("$.counts.accepted").value(4));
        var one = jdbc.queryForMap("select * from historical_claims where source_record_id = 'HC-1' and organization_id = ?", admin.organizationId());
        assertThat(one.get("vehicle_token")).isEqualTo("VEH-Padded");
        assertThat(one.get("claim_type")).isEqualTo("COLLISION");
        assertThat(one.get("claim_status")).isEqualTo("CLOSED_PAID");
        assertThat(one.get("outcome_status")).isEqualTo("NO_ACTION");
        assertThat(one.get("incident_at_precision")).isEqualTo("DATE");
        assertThat(((java.sql.Timestamp) one.get("incident_at")).toInstant()).isEqualTo(java.time.Instant.parse("2025-03-13T18:30:00Z"));
        assertThat((java.math.BigDecimal) one.get("claimed_amount")).isEqualByComparingTo("38500.00");
        assertThat(((java.math.BigDecimal) one.get("claimed_amount")).scale()).isEqualTo(2);
        assertThat(one.get("repair_summary")).isEqualTo("front bumper replaced");
        assertThat(jdbc.queryForObject("select damage_area_codes::text from historical_claims where source_record_id = 'HC-1' and organization_id = ?", String.class, admin.organizationId()))
                .isEqualTo("[\"FRONT_BUMPER\", \"BONNET\"]");
        assertThat(one.get("content_hash").toString()).matches("[0-9a-f]{64}");

        assertThat(jdbc.queryForObject("select repair_summary from historical_claims where source_record_id = 'HC-2' and organization_id = ?", String.class, admin.organizationId()))
                .isEqualTo("=HYPERLINK(\"http://example.invalid\")");
        assertThat(jdbc.queryForObject("select repair_summary from historical_claims where source_record_id = 'HC-3' and organization_id = ?", String.class, admin.organizationId()))
                .isEqualTo("line one line two, with a comma and \"quotes\"");
        assertThat(jdbc.queryForObject("select incident_at_precision from historical_claims where source_record_id = 'HC-3' and organization_id = ?", String.class, admin.organizationId()))
                .isEqualTo("DATETIME");
        assertThat(jdbc.queryForObject("select repair_summary from historical_claims where source_record_id = 'HC-4' and organization_id = ?", String.class, admin.organizationId()))
                .isEqualTo("बम्पर बदला");
    }

    @Test
    void theEndingOfLinesAndATrailingBlankLineDoNotChangeWhatIsStored() throws Exception {
        TestActors admin = admin();
        String crlf = claims(claim("1"), claim("2").vehicle("VEH-2"));
        String lf = crlf.replace("\r\n", "\n") + "\n\n";

        importClaimsOk(admin, crlf);
        UUID again = importClaimsOk(admin, lf);

        getImport(admin, again).andExpect(jsonPath("$.counts.skippedDuplicate").value(2)).andExpect(jsonPath("$.counts.accepted").value(0));
        assertThat(count("historical_claims", admin.organizationId())).isEqualTo(2);
    }

    @Test
    void aByteOrderMarkInFrontOfTheHeaderIsAccepted() throws Exception {
        TestActors admin = admin();
        byte[] bom = ("﻿" + claims(claim("1"))).getBytes(StandardCharsets.UTF_8);

        importBytes(admin, bom, "HISTORICAL_CLAIMS", "1", newKey(), null).andExpect(jsonPath("$.status").value("COMPLETED")).andExpect(jsonPath("$.counts.accepted").value(1));
    }

    @Test
    void noIdentifierOrTextFromAFileEverAppearsInAnyResponse() throws Exception {
        TestActors admin = admin();
        String csv = claims(claim("1").vehicle("LEAK-VEH-TOKEN-1").policy("LEAK-POL-TOKEN-1").claimant("LEAK-CLT-TOKEN-1").summary("LEAK-SUMMARY-TEXT"),
                claim("2").vehicle("LEAK-VEH-TOKEN-2").type("LEAK-BAD-TYPE").incident("LEAK-BAD-DATE").claimed("LEAK-BAD-AMOUNT"));

        UUID id = importClaimsOk(admin, csv);

        List<String> bodies = List.of(
                getImport(admin, id).andReturn().getResponse().getContentAsString(),
                getRows(admin, id).andReturn().getResponse().getContentAsString(),
                getRows(admin, id, "status", "REJECTED").andReturn().getResponse().getContentAsString(),
                mockMvc.perform(get("/api/v1/historical-imports").header("Authorization", admin.bearer())).andReturn().getResponse().getContentAsString());
        for (String body : bodies) {
            assertThat(body).doesNotContain("LEAK-");
        }
    }
}
