package com.example.ClaimShieldX.history;

import com.example.ClaimShieldX.history.SyntheticHistory.Dataset;
import com.example.ClaimShieldX.history.SyntheticHistory.DemoClaim;
import com.example.ClaimShieldX.support.TestActors;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The synthetic history can be loaded repeatably and answers each designed scenario the way the product should. */
class SyntheticHistoryTests extends HistoryApiTestBase {
    private static final Dataset DATA = SyntheticHistory.generate(42);

    private UUID demoClaim(TestActors actor, DemoClaim demo) {
        return claimWith(actor, demo.internalId(), demo.vehicle(), demo.policy(), demo.claimant(), demo.garage(), SyntheticHistory.DEMO_INCIDENT);
    }

    private TestActors loaded() throws Exception {
        TestActors admin = admin();
        importClaimsOk(admin, DATA.claimsCsv());
        importGaragesOk(admin, DATA.garageCsv());
        return admin;
    }

    // ------------------------------------------------------------ the dataset itself

    @Test
    void theDatasetMatchesTheScaleTheProductRequirementsSuggest() {
        assertThat(DATA.claimRows()).isBetween(100, 300);
        assertThat(DATA.vehicles().size()).isBetween(50, 150);
        assertThat(DATA.garages().size()).isBetween(10, 30);
        assertThat(DATA.garageRows()).isEqualTo(DATA.garages().size() * 3); // three reporting periods per garage
    }

    @Test
    void theSameSeedAlwaysGivesTheSameFilesAndAnotherSeedGivesDifferentOnes() {
        Dataset again = SyntheticHistory.generate(42);
        Dataset other = SyntheticHistory.generate(43);

        assertThat(again.claimsCsv()).isEqualTo(DATA.claimsCsv());
        assertThat(again.garageCsv()).isEqualTo(DATA.garageCsv());
        assertThat(other.claimsCsv()).isNotEqualTo(DATA.claimsCsv());
    }

    @Test
    void theDatasetContainsOnlyTokensAndNoPersonalDataPatterns() {
        String all = DATA.claimsCsv() + DATA.garageCsv();

        assertThat(all).doesNotContainPattern("[A-Z]{2}\\d{2}[A-Z]{1,2}\\d{4}"); // no vehicle registration plates
        assertThat(all).doesNotContainPattern("\\b\\d{10}\\b"); // no phone numbers
        assertThat(all).doesNotContain("@").doesNotContainIgnoringCase("street").doesNotContainIgnoringCase("road");
        assertThat(all).doesNotContainIgnoringCase("scenario").doesNotContainIgnoringCase("fraud").doesNotContainIgnoringCase("expected");
    }

    // ------------------------------------------------------------ loading it, repeatably

    @Test
    void everyRowOfTheDatasetIsAcceptedBothFilesLoadCompletely() throws Exception {
        TestActors admin = admin();

        long started = System.nanoTime();
        UUID claims = importClaimsOk(admin, DATA.claimsCsv());
        UUID garages = importGaragesOk(admin, DATA.garageCsv());
        System.out.println("SYNTHETIC history loaded in " + (System.nanoTime() - started) / 1_000_000 + " ms");

        getImport(admin, claims).andExpect(jsonPath("$.status").value("COMPLETED")).andExpect(jsonPath("$.counts.totalRows").value(DATA.claimRows()))
                .andExpect(jsonPath("$.counts.accepted").value(DATA.claimRows())).andExpect(jsonPath("$.counts.rejected").value(0));
        getImport(admin, garages).andExpect(jsonPath("$.status").value("COMPLETED")).andExpect(jsonPath("$.counts.accepted").value(DATA.garageRows()));
    }

    @Test
    void loadingItAgainChangesNothing() throws Exception {
        TestActors admin = loaded();
        String before = jdbc.queryForObject("select md5(string_agg(source_record_id || content_hash, ',' order by source_record_id)) from historical_claims where organization_id = ?",
                String.class, admin.organizationId());

        UUID again = importClaimsOk(admin, DATA.claimsCsv());
        UUID garagesAgain = importGaragesOk(admin, DATA.garageCsv());

        getImport(admin, again).andExpect(jsonPath("$.counts.accepted").value(0)).andExpect(jsonPath("$.counts.skippedDuplicate").value(DATA.claimRows()));
        getImport(admin, garagesAgain).andExpect(jsonPath("$.counts.skippedDuplicate").value(DATA.garageRows()));
        assertThat(count("historical_claims", admin.organizationId())).isEqualTo(DATA.claimRows());
        assertThat(count("garage_history_records", admin.organizationId())).isEqualTo(DATA.garageRows());
        assertThat(jdbc.queryForObject("select md5(string_agg(source_record_id || content_hash, ',' order by source_record_id)) from historical_claims where organization_id = ?",
                String.class, admin.organizationId())).isEqualTo(before);
    }

    @Test
    void aSecondOrganizationCanLoadTheSameDatasetIndependently() throws Exception {
        TestActors one = loaded();
        TestActors two = loaded();

        assertThat(count("historical_claims", one.organizationId())).isEqualTo(DATA.claimRows());
        assertThat(count("historical_claims", two.organizationId())).isEqualTo(DATA.claimRows());
    }

    // ------------------------------------------------------------ the designed scenarios

    @Test
    void possibleRepeatDamageShowsBothEarlierClaimsOnTheVehicleClosestFirstWithTheirDamageAreas() throws Exception {
        TestActors admin = loaded();

        matches(admin, demoClaim(admin, SyntheticHistory.REPEAT_DAMAGE)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("MATCHES_FOUND"))
                .andExpect(jsonPath("$.totalPriorClaims").value(2))
                .andExpect(jsonPath("$.matches[0].externalClaimId").value("CLM-SYN-REPEAT-B")).andExpect(jsonPath("$.matches[0].daysBetween").value(45))
                .andExpect(jsonPath("$.matches[0].timing").value("EARLIER")).andExpect(jsonPath("$.matches[0].strength").value("EXACT"))
                .andExpect(jsonPath("$.matches[0].damageAreaCodes", contains("FRONT_BUMPER")))
                .andExpect(jsonPath("$.matches[0].matchedFields", contains("vehicleToken", "garageId")))
                .andExpect(jsonPath("$.matches[1].externalClaimId").value("CLM-SYN-REPEAT-A")).andExpect(jsonPath("$.matches[1].daysBetween").value(90))
                .andExpect(jsonPath("$.matches[1].damageAreaCodes", contains("FRONT_BUMPER", "BONNET")))
                // The policy was renewed (a different token) yet the vehicle still links the claims: the vehicle token is the stable key.
                .andExpect(jsonPath("$.matches[*].matchedFields[*]", not(hasItem("policyToken"))))
                .andExpect(jsonPath("$.matches[0].explanation").value(containsString("not a finding by itself")));
    }

    @Test
    void aLargeGarageWithManyUnrelatedClaimsIsContextAndNeverAMatch() throws Exception {
        TestActors admin = loaded();

        matches(admin, demoClaim(admin, SyntheticHistory.BENIGN_GARAGE)).andExpect(jsonPath("$.status").value("NO_MATCH_FOUND")).andExpect(jsonPath("$.matches", hasSize(0)))
                .andExpect(jsonPath("$.garage.importedHistoricalClaims").value(60)).andExpect(jsonPath("$.garage.records", hasSize(3)))
                .andExpect(jsonPath("$.garage.caution").value(containsString("never evidence of wrongdoing")));
    }

    @Test
    void aFleetVehicleListsItsManyClaimsInOrderOfClosenessAndCapsNothingAtTwelve() throws Exception {
        TestActors admin = loaded();

        String body = matches(admin, demoClaim(admin, SyntheticHistory.FLEET)).andExpect(jsonPath("$.totalPriorClaims").value(12)).andExpect(jsonPath("$.matches", hasSize(12)))
                .andExpect(jsonPath("$.truncated").value(false)).andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<List<Integer>>read(body, "$.matches[*].daysBetween")).isSorted();
        assertThat(JsonPath.<List<String>>read(body, "$.matches[*].strength")).containsOnly("EXACT");
    }

    @Test
    void aVehicleWithNoHistoryHasNoMatchEvenThoughHistoryExists() throws Exception {
        TestActors admin = loaded();

        matches(admin, demoClaim(admin, SyntheticHistory.CLEAN)).andExpect(jsonPath("$.status").value("NO_MATCH_FOUND"))
                .andExpect(jsonPath("$.dataAvailability.historicalClaimRows").value(DATA.claimRows())).andExpect(jsonPath("$.matches", hasSize(0)));
    }

    @Test
    void aSharedPolicyOrClaimantTokenAloneIsOnlyWeakContext() throws Exception {
        TestActors admin = loaded();

        matches(admin, demoClaim(admin, SyntheticHistory.FAMILY_POLICY)).andExpect(jsonPath("$.totalPriorClaims").value(2))
                .andExpect(jsonPath("$.matches[*].strength", everyItem(is("CONTEXT")))).andExpect(jsonPath("$.matches[*].relation", everyItem(is("SAME_POLICY"))))
                .andExpect(jsonPath("$.matches[0].ambiguities", hasItem("WEAK_TOKEN_ONLY")));
        matches(admin, demoClaim(admin, SyntheticHistory.SHARED_CLAIMANT)).andExpect(jsonPath("$.totalPriorClaims").value(2))
                .andExpect(jsonPath("$.matches[*].relation", everyItem(is("SAME_CLAIMANT")))).andExpect(jsonPath("$.matches[*].strength", everyItem(is("CONTEXT"))));
    }

    @Test
    void anEarlierClaimRecordedInTwoVersionsIsOneEntryShowingTheLatest() throws Exception {
        TestActors admin = loaded();

        matches(admin, demoClaim(admin, SyntheticHistory.VERSIONS)).andExpect(jsonPath("$.totalPriorClaims").value(1)).andExpect(jsonPath("$.matches[0].claimVersion").value(2))
                .andExpect(jsonPath("$.matches[0].claimStatus").value("CLOSED_PAID")).andExpect(jsonPath("$.matches[0].versionsMatched").value(2))
                .andExpect(jsonPath("$.matches[0].ambiguities", hasItem("MULTIPLE_VERSIONS")));
    }

    @Test
    void anEarlierClaimStillUnderReviewIsShownAsSuchWithoutAnyOutcomeAndWithoutAccusation() throws Exception {
        TestActors admin = loaded();

        String body = matches(admin, demoClaim(admin, SyntheticHistory.SUSPICION_NO_OUTCOME)).andExpect(jsonPath("$.matches[0].claimStatus").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.matches[0].outcomeStatus").doesNotExist()).andExpect(jsonPath("$.matches[0].ambiguities", hasItem("OUTCOME_NOT_RECORDED")))
                .andReturn().getResponse().getContentAsString();

        String matchText = JsonPath.read(body, "$.matches[0].explanation") + " " + JsonPath.<List<String>>read(body, "$.matches[0].ambiguities");
        assertThat(matchText.toLowerCase()).doesNotContain("fraud").doesNotContain("suspicious").doesNotContain("guilty").doesNotContain("confirmed").doesNotContain("cleared");
        assertThat(JsonPath.<Object>read(body, "$.matches[0].claimStatus")).isEqualTo("UNDER_REVIEW");
    }

    @Test
    void anEarlierClaimOnTheSameCalendarDayAdmitsThatTheOrderIsUnknown() throws Exception {
        TestActors admin = loaded();

        matches(admin, demoClaim(admin, SyntheticHistory.SAME_DAY)).andExpect(jsonPath("$.matches[0].timing").value("SAME_DAY"))
                .andExpect(jsonPath("$.matches[0].ambiguities", hasItem("ORDER_UNKNOWN_SAME_DAY"))).andExpect(jsonPath("$.matches[0].incidentAtPrecision").value("DATE"));
    }
}
