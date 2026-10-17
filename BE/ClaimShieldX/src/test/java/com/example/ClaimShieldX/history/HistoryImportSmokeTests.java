package com.example.ClaimShieldX.history;

import com.example.ClaimShieldX.support.TestActors;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HistoryImportSmokeTests extends HistoryApiTestBase {
    @Test
    void aValidFileIsImportedAndAClaimFindsItsEarlierClaim() throws Exception {
        TestActors admin = admin();
        UUID importId = importClaimsOk(admin, HistoryCsv.claims(HistoryCsv.claim("1"), HistoryCsv.claim("2").vehicle("VEH-OTHER")));

        getImport(admin, importId).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.counts.totalRows").value(2)).andExpect(jsonPath("$.counts.accepted").value(2));
        getRows(admin, importId).andExpect(jsonPath("$.items[0].status").value("ACCEPTED"));

        UUID claim = claimWith(admin, "CLM-NEW", "VEH-TOKEN-0001", "POL-X", null, "GARAGE-DEMO-01", Instant.parse("2025-05-01T05:00:00Z"));
        matches(admin, claim).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("MATCHES_FOUND"))
                .andExpect(jsonPath("$.matches[0].externalClaimId").value("CLM-HIST-1")).andExpect(jsonPath("$.matches[0].strength").value("EXACT"));
    }
}
