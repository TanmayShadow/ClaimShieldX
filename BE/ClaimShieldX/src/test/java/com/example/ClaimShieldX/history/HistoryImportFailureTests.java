package com.example.ClaimShieldX.history;

import com.example.ClaimShieldX.support.TestActors;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static com.example.ClaimShieldX.history.HistoryCsv.claim;
import static com.example.ClaimShieldX.history.HistoryCsv.claims;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A file that cannot be used at all is never a silent nothing: the request succeeds, the import exists as a FAILED record with a
 * code and a safe detail, and not one row of the file is stored.
 */
class HistoryImportFailureTests extends HistoryApiTestBase {
    private UUID assertFailed(TestActors admin, byte[] bytes, String type, String code) throws Exception {
        String body = importBytes(admin, bytes, type, "1", newKey(), null).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("FAILED")).andExpect(jsonPath("$.failureCode").value(code))
                .andExpect(jsonPath("$.failureDetail").isNotEmpty()).andExpect(jsonPath("$.counts.totalRows").value(0))
                .andExpect(jsonPath("$.counts.accepted").value(0)).andExpect(jsonPath("$.finishedAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(JsonPath.read(body, "$.id"));
        getImport(admin, id).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FAILED")).andExpect(jsonPath("$.failureCode").value(code));
        getRows(admin, id).andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(0)));
        assertThat(count("historical_claims", admin.organizationId())).isZero();
        assertThat(count("garage_history_records", admin.organizationId())).isZero();
        assertThat(count("historical_import_rows", admin.organizationId())).isZero();
        return id;
    }

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------ not a usable file

    @Test
    void anEmptyFileFails() throws Exception {
        assertFailed(admin(), new byte[0], "HISTORICAL_CLAIMS", "EMPTY_FILE");
    }

    @Test
    void aFileOfOnlyWhitespaceOrOnlyAHeaderHasNoData() throws Exception {
        TestActors admin = admin();

        assertFailed(admin, utf8("  \r\n\t\r\n"), "HISTORICAL_CLAIMS", "EMPTY_FILE");
        assertFailed(admin, utf8(HistoryCsv.CLAIMS_HEADER + "\r\n"), "HISTORICAL_CLAIMS", "EMPTY_FILE");
        assertFailed(admin, utf8(HistoryCsv.GARAGE_HEADER), "GARAGE_HISTORY", "EMPTY_FILE");
    }

    @Test
    void textThatIsNotUtf8OrContainsNulOrIsBinaryFails() throws Exception {
        TestActors admin = admin();
        byte[] latin1 = (HistoryCsv.CLAIMS_HEADER + "\r\n" + claim("1").summary("café").line() + "\r\n").getBytes(StandardCharsets.ISO_8859_1);
        byte[] nul = (claims(claim("1")) + "\u0000").getBytes(StandardCharsets.UTF_8);
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13, 'I', 'H', 'D', 'R'};

        assertFailed(admin, latin1, "HISTORICAL_CLAIMS", "INVALID_ENCODING");
        assertFailed(admin, nul, "HISTORICAL_CLAIMS", "INVALID_ENCODING");
        assertFailed(admin, png, "HISTORICAL_CLAIMS", "INVALID_ENCODING");
    }

    @Test
    void brokenQuotingFails() throws Exception {
        assertFailed(admin(), utf8(HistoryCsv.CLAIMS_HEADER + "\r\n\"HC-1,CLM-1\r\nHC-2\r\n"), "HISTORICAL_CLAIMS", "INVALID_CSV");
    }

    // ------------------------------------------------------------ the header

    @Test
    void aMissingRequiredColumnIsNamedInTheDetail() throws Exception {
        TestActors admin = admin();
        String csv = claims(claim("1")).replace("vehicle_token,", "vehicle,");

        UUID id = assertFailed(admin, utf8(csv), "HISTORICAL_CLAIMS", "INVALID_HEADER");

        getImport(admin, id).andExpect(jsonPath("$.failureDetail").value(org.hamcrest.Matchers.containsString("vehicle_token")))
                .andExpect(jsonPath("$.failureDetail").value(org.hamcrest.Matchers.containsString("1 column(s)")))
                .andExpect(jsonPath("$.failureDetail").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("vehicle,"))));
    }

    @Test
    void anUnknownColumnIsCountedButNeverNamed() throws Exception {
        TestActors admin = admin();
        String csv = claims(claim("1")).replace(",currency,", ",currency,claimant_full_name,").replaceFirst("INR,", "INR,SECRET-NAME,");

        UUID id = assertFailed(admin, utf8(csv), "HISTORICAL_CLAIMS", "INVALID_HEADER");

        String detail = JsonPath.read(getImport(admin, id).andReturn().getResponse().getContentAsString(), "$.failureDetail");
        assertThat(detail).contains("1 column(s)").doesNotContain("claimant_full_name").doesNotContain("SECRET-NAME");
    }

    @Test
    void aRepeatedOrEmptyColumnNameFails() throws Exception {
        TestActors admin = admin();

        assertFailed(admin, utf8(claims(claim("1")).replace(",claim_type,", ",vehicle_token,")), "HISTORICAL_CLAIMS", "INVALID_HEADER");
        assertFailed(admin, utf8(claims(claim("1")).replace(",claim_type,", ",,")), "HISTORICAL_CLAIMS", "INVALID_HEADER");
    }

    @Test
    void aFileWhoseFirstRowIsDataFailsWithoutEchoingThatData() throws Exception {
        TestActors admin = admin();
        String noHeader = claim("1").vehicle("LEAK-VEH-TOKEN").line() + "\r\n" + claim("2").line() + "\r\n";

        UUID id = assertFailed(admin, utf8(noHeader), "HISTORICAL_CLAIMS", "INVALID_HEADER");

        String body = getImport(admin, id).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("LEAK-VEH-TOKEN").doesNotContain("HC-1");
    }

    // ------------------------------------------------------------ size and row limits at the defaults

    @Test
    void aFileWithTenThousandRowsIsAcceptedInFull() throws Exception {
        TestActors admin = admin();
        HistoryCsv.ClaimRow[] rows = manyRows(10_000);

        long started = System.nanoTime();
        String body = importCsv(admin, claims(rows), "HISTORICAL_CLAIMS").andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.counts.totalRows").value(10_000)).andExpect(jsonPath("$.counts.accepted").value(10_000)).andReturn().getResponse().getContentAsString();
        System.out.println("IMPORT of 10,000 rows took " + (System.nanoTime() - started) / 1_000_000 + " ms");

        assertThat(count("historical_claims", admin.organizationId())).isEqualTo(10_000);
        assertThat(count("historical_import_rows", admin.organizationId())).isEqualTo(10_000);
        getRows(admin, UUID.fromString(JsonPath.read(body, "$.id")), "size", "100", "page", "99").andExpect(jsonPath("$.items", hasSize(100)))
                .andExpect(jsonPath("$.items[99].rowNumber").value(10_000));
    }

    @Test
    void oneRowOverTheLimitFailsTheWholeFileAndStoresNothing() throws Exception {
        TestActors admin = admin();

        assertFailed(admin, utf8(claims(manyRows(10_001))), "HISTORICAL_CLAIMS", "TOO_MANY_ROWS");
    }

    @Test
    void aFileOverFiveMebibytesIsNotReadAtAll() throws Exception {
        TestActors admin = admin();
        byte[] big = new byte[5 * 1024 * 1024 + 1];
        java.util.Arrays.fill(big, (byte) 'a');

        UUID id = assertFailed(admin, big, "HISTORICAL_CLAIMS", "FILE_TOO_LARGE");

        getImport(admin, id).andExpect(jsonPath("$.sizeBytes").value(big.length)).andExpect(jsonPath("$.sha256").isNotEmpty());
    }

    @Test
    void aFailedImportCanBeFollowedByACorrectedOne() throws Exception {
        TestActors admin = admin();
        assertFailed(admin, utf8(claims(claim("1")).replace("vehicle_token", "vehicle")), "HISTORICAL_CLAIMS", "INVALID_HEADER");

        importCsv(admin, claims(claim("1")), "HISTORICAL_CLAIMS").andExpect(jsonPath("$.status").value("COMPLETED")).andExpect(jsonPath("$.counts.accepted").value(1));

        assertThat(count("historical_imports", admin.organizationId())).isEqualTo(2);
        assertThat(count("historical_claims", admin.organizationId())).isEqualTo(1);
    }

    @Test
    void failedImportsAreListedLikeAnyOtherAndTheirReplayReturnsTheSameFailure() throws Exception {
        TestActors admin = admin();
        String key = newKey();
        ResultActions first = importBytes(admin, new byte[0], "HISTORICAL_CLAIMS", "1", key, null);
        String id = JsonPath.read(first.andReturn().getResponse().getContentAsString(), "$.id");

        importBytes(admin, new byte[0], "HISTORICAL_CLAIMS", "1", key, null).andExpect(status().isAccepted())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Idempotent-Replay", "true"))
                .andExpect(jsonPath("$.id").value(id)).andExpect(jsonPath("$.status").value("FAILED"));

        assertThat(count("historical_imports", admin.organizationId())).isEqualTo(1);
    }

    static HistoryCsv.ClaimRow[] manyRows(int n) {
        HistoryCsv.ClaimRow[] rows = new HistoryCsv.ClaimRow[n];
        for (int i = 0; i < n; i++) {
            rows[i] = claim(String.format("%06d", i)).vehicle("VEH-" + (i % 700)).incident("2025-03-" + String.format("%02d", 1 + (i % 14)));
        }
        return rows;
    }

    @SuppressWarnings("unused")
    private static List<String> unused() {
        return List.of();
    }
}
