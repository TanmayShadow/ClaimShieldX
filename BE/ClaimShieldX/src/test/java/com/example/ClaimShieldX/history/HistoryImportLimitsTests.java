package com.example.ClaimShieldX.history;

import com.example.ClaimShieldX.support.TestActors;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import java.nio.charset.StandardCharsets;

import static com.example.ClaimShieldX.history.HistoryCsv.claim;
import static com.example.ClaimShieldX.history.HistoryCsv.claims;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The exact edges of the two limits, with small limits so the files are cheap to build. */
@TestPropertySource(properties = {"app.history.max-file-bytes=2000", "app.history.max-rows=5"})
class HistoryImportLimitsTests extends HistoryApiTestBase {
    /** A valid file padded with blank lines (which the reader ignores) to exactly this many bytes. */
    private static byte[] fileOfExactly(int size) {
        String csv = claims(claim("1"));
        StringBuilder padded = new StringBuilder(csv);
        while (padded.length() < size) {
            padded.append(padded.length() + 2 <= size ? "\r\n" : "\n");
        }
        byte[] bytes = padded.toString().getBytes(StandardCharsets.UTF_8);
        assertThat(bytes).hasSize(size);
        return bytes;
    }

    @Test
    void aFileOfExactlyTheLimitIsReadAndOneByteMoreIsNot() throws Exception {
        TestActors admin = admin();

        importBytes(admin, fileOfExactly(2000), "HISTORICAL_CLAIMS", "1", newKey(), null).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("COMPLETED")).andExpect(jsonPath("$.sizeBytes").value(2000));
        importBytes(admin, fileOfExactly(2001), "HISTORICAL_CLAIMS", "1", newKey(), null).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("FAILED")).andExpect(jsonPath("$.failureCode").value("FILE_TOO_LARGE"));
    }

    @Test
    void exactlyTheRowLimitIsAcceptedAndOneMoreFailsTheWholeFile() throws Exception {
        TestActors admin = admin();
        HistoryCsv.ClaimRow[] five = new HistoryCsv.ClaimRow[5];
        HistoryCsv.ClaimRow[] six = new HistoryCsv.ClaimRow[6];
        for (int i = 0; i < 6; i++) {
            HistoryCsv.ClaimRow row = claim("R" + i).vehicle("V" + i);
            row.summary(null);
            if (i < 5) {
                five[i] = row;
            }
            six[i] = row;
        }

        importCsv(admin, claims(five), "HISTORICAL_CLAIMS").andExpect(jsonPath("$.status").value("COMPLETED")).andExpect(jsonPath("$.counts.accepted").value(5));
        importCsv(admin, claims(six), "HISTORICAL_CLAIMS").andExpect(jsonPath("$.status").value("FAILED")).andExpect(jsonPath("$.failureCode").value("TOO_MANY_ROWS"));

        assertThat(count("historical_claims", admin.organizationId())).isEqualTo(5);
    }
}
