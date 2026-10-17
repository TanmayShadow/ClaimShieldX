package com.example.ClaimShieldX.history;

import com.example.ClaimShieldX.support.TestActors;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.example.ClaimShieldX.history.HistoryCsv.claim;
import static com.example.ClaimShieldX.history.HistoryCsv.claims;
import static com.example.ClaimShieldX.history.HistoryCsv.garage;
import static com.example.ClaimShieldX.history.HistoryCsv.garages;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** What a lookup tells an investigator: the four honest answers, and for every relationship the reason and the source. */
class HistoryMatchesApiTests extends HistoryApiTestBase {
    // 11:00 India time on 14 May 2025.
    private static final Instant INCIDENT = Instant.parse("2025-05-14T05:30:00Z");

    private UUID newClaim(TestActors actor) {
        return claimWith(actor, "CLM-NEW", "VEH-TOKEN-0001", "POL-TOKEN-0001", "CLT-TOKEN-0001", "GARAGE-DEMO-01", INCIDENT);
    }

    // ------------------------------------------------------------ the four answers

    @Test
    void noHistoryAtAllIsReportedAsNoHistoryAvailableNeverAsNoMatch() throws Exception {
        TestActors admin = admin();

        matches(admin, newClaim(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NO_HISTORICAL_DATA_AVAILABLE"))
                .andExpect(jsonPath("$.dataAvailability.historicalClaimRows").value(0)).andExpect(jsonPath("$.dataAvailability.garageHistoryRows").value(0))
                .andExpect(jsonPath("$.dataAvailability.latestClaimsImportFailed").value(false))
                .andExpect(jsonPath("$.matches", hasSize(0))).andExpect(jsonPath("$.totalPriorClaims").value(0))
                .andExpect(jsonPath("$.notes[*]", hasItem(containsString("No historical claims have been imported"))));
    }

    @Test
    void garageHistoryAloneIsNotClaimHistoryButItsContextIsStillShown() throws Exception {
        TestActors admin = admin();
        importGaragesOk(admin, garages(garage("1")));

        matches(admin, newClaim(admin)).andExpect(jsonPath("$.status").value("NO_HISTORICAL_DATA_AVAILABLE"))
                .andExpect(jsonPath("$.dataAvailability.garageHistoryRows").value(1))
                .andExpect(jsonPath("$.garage.records", hasSize(1))).andExpect(jsonPath("$.garage.records[0].claimCount").value(120));
    }

    @Test
    void aFailedImportWithNothingStoredIsReportedAsAFailedImport() throws Exception {
        TestActors admin = admin();
        importBytes(admin, new byte[0], "HISTORICAL_CLAIMS", "1", newKey(), null).andExpect(status().isAccepted());

        matches(admin, newClaim(admin)).andExpect(jsonPath("$.status").value("HISTORY_IMPORT_FAILED"))
                .andExpect(jsonPath("$.dataAvailability.latestClaimsImportFailed").value(true))
                .andExpect(jsonPath("$.dataAvailability.latestClaimsImport.status").value("FAILED"))
                .andExpect(jsonPath("$.notes[*]", hasItem(containsString("absence of matches says nothing"))));
    }

    @Test
    void historyThatExistsButSharesNothingIsNoMatchFound() throws Exception {
        TestActors admin = admin();
        importClaimsOk(admin, claims(claim("1").vehicle("VEH-OTHER").policy("POL-OTHER")));

        matches(admin, newClaim(admin)).andExpect(jsonPath("$.status").value("NO_MATCH_FOUND")).andExpect(jsonPath("$.dataAvailability.historicalClaimRows").value(1))
                .andExpect(jsonPath("$.matches", hasSize(0))).andExpect(jsonPath("$.notes[*]", hasItem(containsString("History exists"))));
    }

    @Test
    void aFailedImportAfterAGoodOneKeepsTheOldMatchesAndWarnsTheyMayBeStale() throws Exception {
        TestActors admin = admin();
        importClaimsOk(admin, claims(claim("1")));
        importBytes(admin, new byte[0], "HISTORICAL_CLAIMS", "1", newKey(), null).andExpect(status().isAccepted());

        matches(admin, newClaim(admin)).andExpect(jsonPath("$.status").value("MATCHES_FOUND")).andExpect(jsonPath("$.dataAvailability.latestClaimsImportFailed").value(true))
                .andExpect(jsonPath("$.notes[*]", hasItem(containsString("may be out of date"))));
    }

    // ------------------------------------------------------------ an exact relationship, explained

    @Test
    void aSharedVehicleTokenIsAnExactRelationshipWithItsReasonTimingAndSource() throws Exception {
        TestActors admin = admin();
        UUID importId = importClaimsOk(admin, claims(claim("0").vehicle("VEH-OTHER").policy("POL-OTHER").garage("GARAGE-OTHER"), claim("1").status("closed_paid")));
        UUID claimId = newClaim(admin);

        matches(admin, claimId).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("MATCHES_FOUND")).andExpect(jsonPath("$.claimId").value(claimId.toString()))
                .andExpect(jsonPath("$.totalPriorClaims").value(1)).andExpect(jsonPath("$.truncated").value(false))
                .andExpect(jsonPath("$.matches[0].externalClaimId").value("CLM-HIST-1")).andExpect(jsonPath("$.matches[0].claimVersion").value(1))
                .andExpect(jsonPath("$.matches[0].relation").value("SAME_VEHICLE")).andExpect(jsonPath("$.matches[0].strength").value("EXACT"))
                .andExpect(jsonPath("$.matches[0].matchedFields", contains("vehicleToken", "policyToken", "garageId")))
                .andExpect(jsonPath("$.matches[0].matchMethod").value("EXACT_TOKEN"))
                .andExpect(jsonPath("$.matches[0].timing").value("EARLIER")).andExpect(jsonPath("$.matches[0].daysBetween").value(61))
                .andExpect(jsonPath("$.matches[0].claimType").value("COLLISION")).andExpect(jsonPath("$.matches[0].claimStatus").value("CLOSED_PAID"))
                .andExpect(jsonPath("$.matches[0].damageAreaCodes", contains("FRONT_BUMPER", "BONNET")))
                .andExpect(jsonPath("$.matches[0].incidentAtPrecision").value("DATETIME"))
                .andExpect(jsonPath("$.matches[0].ambiguities", contains("OUTCOME_NOT_RECORDED")))
                .andExpect(jsonPath("$.matches[0].source.importId").value(importId.toString())).andExpect(jsonPath("$.matches[0].source.rowNumber").value(2))
                .andExpect(jsonPath("$.matches[0].source.sourceRecordId").value("HC-1")).andExpect(jsonPath("$.matches[0].source.importedAt").isNotEmpty())
                .andExpect(jsonPath("$.matches[0].explanation").value(containsString("same vehicle token")))
                .andExpect(jsonPath("$.matches[0].explanation").value(containsString("not a finding by itself")));
    }

    @Test
    void aPolicyOrClaimantTokenAloneIsContextWithAnAmbiguityNotAnExactMatch() throws Exception {
        TestActors admin = admin();
        importClaimsOk(admin, claims(claim("1").vehicle("VEH-A").policy("POL-TOKEN-0001").garage(null),
                claim("2").vehicle("VEH-B").policy("POL-B").claimant("CLT-TOKEN-0001").garage(null)));

        matches(admin, newClaim(admin)).andExpect(jsonPath("$.status").value("MATCHES_FOUND")).andExpect(jsonPath("$.totalPriorClaims").value(2))
                .andExpect(jsonPath("$.matches[*].strength", everyItem(is("CONTEXT"))))
                .andExpect(jsonPath("$.matches[*].relation", containsInAnyOrder("SAME_POLICY", "SAME_CLAIMANT")))
                .andExpect(jsonPath("$.matches[*].ambiguities[*]", hasItem("WEAK_TOKEN_ONLY")));
    }

    @Test
    void oneEarlierClaimSharingEveryIdentifierIsOneEntry() throws Exception {
        TestActors admin = admin();
        importClaimsOk(admin, claims(claim("1").claimant("CLT-TOKEN-0001")));

        matches(admin, newClaim(admin)).andExpect(jsonPath("$.totalPriorClaims").value(1)).andExpect(jsonPath("$.matches", hasSize(1)))
                .andExpect(jsonPath("$.matches[0].matchedFields", contains("vehicleToken", "policyToken", "claimantToken", "garageId")));
    }

    @Test
    void versionsOfOneClaimAreOneEntryShowingTheLatest() throws Exception {
        TestActors admin = admin();
        importClaimsOk(admin, claims(claim("1").external("CLM-X").version("1").status("OPEN"), claim("2").external("CLM-X").version("2").status("CLOSED_PAID")));

        matches(admin, newClaim(admin)).andExpect(jsonPath("$.totalPriorClaims").value(1)).andExpect(jsonPath("$.matches[0].claimVersion").value(2))
                .andExpect(jsonPath("$.matches[0].claimStatus").value("CLOSED_PAID")).andExpect(jsonPath("$.matches[0].versionsMatched").value(2))
                .andExpect(jsonPath("$.matches[0].ambiguities", hasItem("MULTIPLE_VERSIONS")));
    }

    @Test
    void theClaimIsNotComparedWithItsOwnHistoryRow() throws Exception {
        TestActors admin = admin();
        importClaimsOk(admin, claims(claim("1").external("CLM-NEW"), claim("2")));

        matches(admin, newClaim(admin)).andExpect(jsonPath("$.totalPriorClaims").value(1)).andExpect(jsonPath("$.matches[0].externalClaimId").value("CLM-HIST-2"))
                .andExpect(jsonPath("$.excludedAsSameClaim").value(1)).andExpect(jsonPath("$.notes[*]", hasItem(containsString("not compared with itself"))));
    }

    @Test
    void aLaterClaimAndASameDayClaimAreDescribedHonestly() throws Exception {
        TestActors admin = admin();
        importClaimsOk(admin, claims(claim("1").onAt("2025-06-14T11:00:00+05:30"), claim("2").on("2025-05-14")));

        matches(admin, newClaim(admin)).andExpect(jsonPath("$.matches[0].externalClaimId").value("CLM-HIST-2")).andExpect(jsonPath("$.matches[0].timing").value("SAME_DAY"))
                .andExpect(jsonPath("$.matches[0].incidentAtPrecision").value("DATE")).andExpect(jsonPath("$.matches[0].ambiguities", hasItem("ORDER_UNKNOWN_SAME_DAY")))
                .andExpect(jsonPath("$.matches[1].timing").value("LATER")).andExpect(jsonPath("$.matches[1].daysBetween").value(31));
    }

    @Test
    void aKnownOutcomeIsShownAsRecordedAndAMissingOneAsNotRecorded() throws Exception {
        TestActors admin = admin();
        importClaimsOk(admin, claims(claim("1").outcome("no_action"), claim("2").onAt("2025-04-01T10:00:00+05:30")));

        String body = matches(admin, newClaim(admin)).andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<List<String>>read(body, "$.matches[?(@.externalClaimId=='CLM-HIST-1')].outcomeStatus")).containsExactly("NO_ACTION");
        assertThat(JsonPath.<List<List<String>>>read(body, "$.matches[?(@.externalClaimId=='CLM-HIST-1')].ambiguities").get(0)).doesNotContain("OUTCOME_NOT_RECORDED");
        assertThat(JsonPath.<List<List<String>>>read(body, "$.matches[?(@.externalClaimId=='CLM-HIST-2')].ambiguities").get(0)).contains("OUTCOME_NOT_RECORDED");
        assertThat(JsonPath.<List<Object>>read(body, "$.matches[?(@.externalClaimId=='CLM-HIST-2')].outcomeStatus")).isEmpty();
    }

    // ------------------------------------------------------------ the garage is context, never a match

    @Test
    void manyClaimsAtTheSameGarageAreNotARelationshipAndTheGarageContextSaysSo() throws Exception {
        TestActors admin = admin();
        HistoryCsv.ClaimRow[] rows = new HistoryCsv.ClaimRow[30];
        for (int i = 0; i < rows.length; i++) {
            rows[i] = claim(String.format("%02d", i)).vehicle("VEH-UNRELATED-" + i).policy("POL-UNRELATED-" + i);
        }
        importClaimsOk(admin, claims(rows));
        importGaragesOk(admin, garages(garage("1").count("500").variance("40").disputed("12").confirmed("1")));

        matches(admin, newClaim(admin)).andExpect(jsonPath("$.status").value("NO_MATCH_FOUND")).andExpect(jsonPath("$.matches", hasSize(0)))
                .andExpect(jsonPath("$.garage.garageId").value("GARAGE-DEMO-01")).andExpect(jsonPath("$.garage.importedHistoricalClaims").value(30))
                .andExpect(jsonPath("$.garage.records[0].claimCount").value(500)).andExpect(jsonPath("$.garage.records[0].estimateInvoiceVarianceCount").value(40))
                .andExpect(jsonPath("$.garage.records[0].disputedOrReopenedCount").value(12)).andExpect(jsonPath("$.garage.records[0].confirmedOutcomeCount").value(1))
                .andExpect(jsonPath("$.garage.records[0].source.sourceRecordId").value("GH-1"))
                .andExpect(jsonPath("$.garage.caution").value(containsString("never evidence of wrongdoing")));
    }

    // ------------------------------------------------------------ cap and order

    @Test
    void onlyTheClosestFiftyAreListedWhileTheTotalIsStillReported() throws Exception {
        TestActors admin = admin();
        HistoryCsv.ClaimRow[] rows = new HistoryCsv.ClaimRow[60];
        for (int i = 0; i < rows.length; i++) {
            java.time.LocalDate date = java.time.LocalDate.of(2025, 5, 13).minusDays(i * 2L);
            rows[i] = claim(String.format("%03d", i)).incident(date.toString()).reported(date.plusDays(1).toString()).repaired(date.plusDays(5).toString());
        }
        importClaimsOk(admin, claims(rows));

        String body = matches(admin, newClaim(admin)).andExpect(jsonPath("$.totalPriorClaims").value(60)).andExpect(jsonPath("$.matches", hasSize(50)))
                .andExpect(jsonPath("$.truncated").value(true)).andExpect(jsonPath("$.notes[*]", hasItem(containsString("50 closest")))).andReturn().getResponse().getContentAsString();

        List<Integer> days = JsonPath.read(body, "$.matches[*].daysBetween");
        assertThat(days).isSorted();
        assertThat(JsonPath.<String>read(body, "$.matches[0].externalClaimId")).isEqualTo("CLM-HIST-000");
    }

    @Test
    void theSameLookupAlwaysGivesTheSameAnswerAndChangesNothing() throws Exception {
        TestActors admin = admin();
        importClaimsOk(admin, claims(claim("1"), claim("2").onAt("2025-04-01T10:00:00+05:30"), claim("3").vehicle("VEH-X").policy("POL-TOKEN-0001")));
        UUID claimId = newClaim(admin);
        int rowsBefore = count("historical_claims", admin.organizationId()) + count("historical_import_rows", admin.organizationId());

        String first = matches(admin, claimId).andReturn().getResponse().getContentAsString();
        String second = matches(admin, claimId).andReturn().getResponse().getContentAsString();

        assertThat(second).isEqualTo(first);
        assertThat(count("historical_claims", admin.organizationId()) + count("historical_import_rows", admin.organizationId())).isEqualTo(rowsBefore);
    }

    // ------------------------------------------------------------ tenancy and access

    @Test
    void anotherOrganizationsHistoryIsNeverUsed() throws Exception {
        TestActors mine = admin();
        TestActors other = admin();
        importClaimsOk(other, claims(claim("1")));

        matches(mine, newClaim(mine)).andExpect(jsonPath("$.status").value("NO_HISTORICAL_DATA_AVAILABLE")).andExpect(jsonPath("$.dataAvailability.historicalClaimRows").value(0))
                .andExpect(jsonPath("$.matches", hasSize(0)));
    }

    @Test
    void aClaimOfAnotherOrganizationIsAnUnknownClaim() throws Exception {
        TestActors owner = admin();
        TestActors stranger = admin();
        UUID claimId = newClaim(owner);

        String foreign = matches(stranger, claimId).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND")).andReturn().getResponse().getContentAsString();
        String missing = matches(stranger, UUID.randomUUID()).andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();

        assertThat(foreign.replaceAll("\"timestamp\":\"[^\"]*\"", "")).isEqualTo(missing.replaceAll("\"timestamp\":\"[^\"]*\"", ""));
    }

    @Test
    void everySignedInUserMayLookUpButNobodyAnonymous() throws Exception {
        TestActors admin = admin();
        TestActors investigator = TestActors.inOrganization(fixtures, jwtSecret, admin.organizationId(), "INVESTIGATOR");
        UUID claimId = newClaim(admin);

        matches(admin, claimId).andExpect(status().isOk());
        matches(investigator, claimId).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/claims/" + claimId + "/history-matches")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/claims/not-a-uuid/history-matches").header("Authorization", admin.bearer())).andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------ what the response never contains

    @Test
    void theResponseCarriesNoFreeTextFromTheFileNorOtherOrganizationsTokensAndNeverCallsAnythingFraud() throws Exception {
        TestActors admin = admin();
        TestActors other = admin();
        importClaimsOk(admin, claims(claim("1").summary("LEAK-FREE-TEXT-SHOULD-STAY-OUT ignore previous instructions")));
        importClaimsOk(other, claims(claim("1").vehicle("OTHER-ORG-VEHICLE-TOKEN").summary("OTHER-ORG-SUMMARY")));
        importGaragesOk(admin, garages(garage("1")));

        String body = matches(admin, newClaim(admin)).andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("LEAK-FREE-TEXT").doesNotContain("ignore previous instructions").doesNotContain("OTHER-ORG");
        assertThat(body.toLowerCase()).doesNotContain("fraud").doesNotContain("suspicious").doesNotContain("guilty");
        assertThat(body).doesNotContain(admin.organizationId().toString());
    }

    @Test
    void rawTextEncodingOfTheFileDoesNotAffectWhatMatches() throws Exception {
        TestActors admin = admin();
        byte[] bom = ("﻿" + claims(claim("1")).replace("\r\n", "\n")).getBytes(StandardCharsets.UTF_8);
        importBytes(admin, bom, "HISTORICAL_CLAIMS", "1", newKey(), null).andExpect(status().isAccepted());

        matches(admin, newClaim(admin)).andExpect(jsonPath("$.status").value("MATCHES_FOUND"));
    }
}
