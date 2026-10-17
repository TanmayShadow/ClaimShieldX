package com.example.ClaimShieldX.history;

import com.example.ClaimShieldX.support.TestActors;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.UUID;

import static com.example.ClaimShieldX.history.HistoryCsv.claim;
import static com.example.ClaimShieldX.history.HistoryCsv.claims;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/** When more rows share a token than one lookup reads, the answer says it may be incomplete instead of pretending. */
@TestPropertySource(properties = {"app.history.max-candidates=3", "app.history.max-matches=2"})
class HistoryMatchesLimitsTests extends HistoryApiTestBase {
    @Test
    void aLookupThatHitTheReadLimitSaysTheListMayBeIncomplete() throws Exception {
        TestActors admin = admin();
        HistoryCsv.ClaimRow[] rows = new HistoryCsv.ClaimRow[5];
        for (int i = 0; i < rows.length; i++) {
            rows[i] = claim("R" + i).incident("2025-0" + (i + 1) + "-01").reported("2025-0" + (i + 1) + "-02").repaired("2025-0" + (i + 1) + "-05");
        }
        importClaimsOk(admin, claims(rows));
        UUID claimId = claimWith(admin, "CLM-NEW", "VEH-TOKEN-0001", "POL-NONE-X", null, "GARAGE-X", Instant.parse("2025-08-01T05:00:00Z"));

        matches(admin, claimId).andExpect(jsonPath("$.truncated").value(true)).andExpect(jsonPath("$.notes[*]", hasItem(containsString("may be incomplete"))));
    }

    @Test
    void theNumberOfListedMatchesFollowsTheConfiguredLimit() throws Exception {
        TestActors admin = admin();
        importClaimsOk(admin, claims(claim("1"), claim("2").onAt("2025-04-01T10:00:00+05:30")));
        UUID claimId = claimWith(admin, "CLM-NEW", "VEH-TOKEN-0001", "POL-NONE-X", null, "GARAGE-X", Instant.parse("2025-08-01T05:00:00Z"));

        matches(admin, claimId).andExpect(jsonPath("$.totalPriorClaims").value(2)).andExpect(jsonPath("$.matches.length()").value(2))
                .andExpect(jsonPath("$.truncated").value(false));
    }
}
