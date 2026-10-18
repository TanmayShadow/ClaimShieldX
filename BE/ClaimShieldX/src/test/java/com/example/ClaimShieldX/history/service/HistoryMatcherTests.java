package com.example.ClaimShieldX.history.service;

import com.example.ClaimShieldX.history.dto.HistoryMatchesResponse.HistoricalMatch;
import com.example.ClaimShieldX.history.dto.HistoryMatchesResponse.Relation;
import com.example.ClaimShieldX.history.dto.HistoryMatchesResponse.Strength;
import com.example.ClaimShieldX.history.dto.HistoryMatchesResponse.Timing;
import com.example.ClaimShieldX.history.model.HistoricalClaim;
import com.example.ClaimShieldX.history.service.HistoryMatcher.ClaimKeys;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class HistoryMatcherTests {
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    // The claim being looked up: incident at 11:00 India time on 14 May 2025.
    private static final Instant INCIDENT = Instant.parse("2025-05-14T05:30:00Z");
    private static final ClaimKeys CLAIM = new ClaimKeys("CLM-NEW", "VEH-1", "POL-1", "CLT-1", "GARAGE-1", INCIDENT);

    /** A history row with nothing in common with the claim unless the test sets it. */
    private static HistoricalClaim row(String external, int version) {
        HistoricalClaim claim = new HistoricalClaim();
        claim.setOrganizationId(UUID.randomUUID());
        claim.setImportId(UUID.randomUUID());
        claim.setRowNumber(1);
        claim.setSourceRecordId("HC-" + external + "-" + version);
        claim.setExternalClaimId(external);
        claim.setClaimVersion(version);
        claim.setVehicleToken("VEH-OTHER");
        claim.setIncidentAt(Instant.parse("2025-03-14T05:30:00Z"));
        claim.setIncidentAtPrecision("DATETIME");
        claim.setClaimType("COLLISION");
        claim.setClaimStatus("CLOSED");
        claim.setCurrency("INR");
        claim.setContentHash("a".repeat(64));
        return claim;
    }

    private static HistoricalClaim vehicle(String external) {
        HistoricalClaim claim = row(external, 1);
        claim.setVehicleToken("VEH-1");
        return claim;
    }

    private static HistoryMatcher.Matched match(HistoricalClaim... rows) {
        return HistoryMatcher.match(CLAIM, List.of(rows), 50, IST);
    }

    // ------------------------------------------------------------ what counts as a match

    @Test
    void anIdenticalVehicleTokenIsAnExactRelationshipWithItsReason() {
        HistoricalMatch m = match(vehicle("CLM-A")).matches().get(0);

        assertThat(m.relation()).isEqualTo(Relation.SAME_VEHICLE);
        assertThat(m.strength()).isEqualTo(Strength.EXACT);
        assertThat(m.matchedFields()).containsExactly("vehicleToken");
        assertThat(m.matchMethod()).isEqualTo("EXACT_TOKEN");
        assertThat(m.explanation()).contains("same vehicle token").contains("CLM-A");
    }

    @Test
    void aPolicyOrClaimantTokenAloneIsOnlyWeakContext() {
        HistoricalClaim policy = row("CLM-P", 1);
        policy.setPolicyToken("POL-1");
        HistoricalClaim claimant = row("CLM-C", 1);
        claimant.setClaimantToken("CLT-1");

        List<HistoricalMatch> matches = match(policy, claimant).matches();

        assertThat(matches).extracting(HistoricalMatch::relation).containsExactlyInAnyOrder(Relation.SAME_POLICY, Relation.SAME_CLAIMANT);
        assertThat(matches).allSatisfy(m -> {
            assertThat(m.strength()).isEqualTo(Strength.CONTEXT);
            assertThat(m.ambiguities()).contains("WEAK_TOKEN_ONLY");
            assertThat(m.explanation()).containsAnyOf("weak context");
        });
    }

    @Test
    void aSharedGarageAloneNeverMakesAMatch() {
        HistoricalClaim sameGarage = row("CLM-G", 1);
        sameGarage.setGarageId("GARAGE-1");

        assertThat(match(sameGarage).matches()).isEmpty();
        assertThat(match(sameGarage).totalPriorClaims()).isZero();
    }

    @Test
    void aSharedGarageIsRecordedOnlyAsExtraContextWhenATokenAlreadyMatched() {
        HistoricalClaim claim = vehicle("CLM-A");
        claim.setGarageId("GARAGE-1");

        assertThat(match(claim).matches().get(0).matchedFields()).containsExactly("vehicleToken", "garageId");
        assertThat(match(claim).matches().get(0).strength()).isEqualTo(Strength.EXACT);
    }

    @Test
    void oneEarlierClaimSharingSeveralIdentifiersIsOneEntryNotSeveral() {
        HistoricalClaim all = vehicle("CLM-A");
        all.setPolicyToken("POL-1");
        all.setClaimantToken("CLT-1");
        all.setGarageId("GARAGE-1");

        HistoryMatcher.Matched matched = match(all);

        assertThat(matched.matches()).hasSize(1);
        assertThat(matched.totalPriorClaims()).isEqualTo(1);
        assertThat(matched.matches().get(0).matchedFields()).containsExactly("vehicleToken", "policyToken", "claimantToken", "garageId");
        assertThat(matched.matches().get(0).relation()).isEqualTo(Relation.SAME_VEHICLE);
    }

    @Test
    void theSameRowReturnedBySeveralQueriesIsConsideredOnce() {
        HistoricalClaim claim = vehicle("CLM-A");
        claim.setPolicyToken("POL-1");

        HistoryMatcher.Matched matched = HistoryMatcher.match(CLAIM, List.of(claim, claim, claim), 50, IST);

        assertThat(matched.matches()).hasSize(1);
    }

    @Test
    void severalVersionsOfOneClaimAreOneEntryShowingTheLatestAndSayingSo() {
        HistoricalClaim v1 = vehicle("CLM-A");
        HistoricalClaim v2 = row("CLM-A", 2);
        v2.setVehicleToken("VEH-1");
        v2.setClaimStatus("REOPENED");
        HistoricalClaim v3 = row("CLM-A", 3);
        v3.setVehicleToken("VEH-1");
        v3.setClaimStatus("CLOSED_PAID");

        HistoricalMatch m = match(v1, v3, v2).matches().get(0);

        assertThat(m.claimVersion()).isEqualTo(3);
        assertThat(m.claimStatus()).isEqualTo("CLOSED_PAID");
        assertThat(m.versionsMatched()).isEqualTo(3);
        assertThat(m.ambiguities()).contains("MULTIPLE_VERSIONS");
    }

    @Test
    void aVersionThatNoLongerMatchesDoesNotHideTheMatchedIdentifiersOfAnotherVersion() {
        HistoricalClaim v1 = vehicle("CLM-A"); // vehicle matches
        HistoricalClaim v2 = row("CLM-A", 2); // later version, vehicle changed, policy matches
        v2.setPolicyToken("POL-1");

        HistoricalMatch m = match(v1, v2).matches().get(0);

        assertThat(m.claimVersion()).isEqualTo(2);
        assertThat(m.matchedFields()).contains("vehicleToken", "policyToken");
        assertThat(m.strength()).isEqualTo(Strength.EXACT);
    }

    @Test
    void theClaimItselfIsNeverItsOwnRelationship() {
        HistoricalClaim self = vehicle("CLM-NEW");
        HistoricalClaim other = vehicle("CLM-OLD");

        HistoryMatcher.Matched matched = match(self, other);

        assertThat(matched.matches()).extracting(HistoricalMatch::externalClaimId).containsExactly("CLM-OLD");
        assertThat(matched.excludedAsSameClaim()).isEqualTo(1);
    }

    // ------------------------------------------------------------ exactness

    @Test
    void tokensAreComparedExactlyIncludingCaseAndNeverFuzzily() {
        HistoricalClaim lower = row("CLM-L", 1);
        lower.setVehicleToken("veh-1");
        HistoricalClaim spaced = row("CLM-S", 1);
        spaced.setVehicleToken("VEH-1 ");
        HistoricalClaim prefix = row("CLM-X", 1);
        prefix.setVehicleToken("VEH-10");

        assertThat(match(lower, spaced, prefix).matches()).isEmpty();
    }

    @Test
    void anAbsentTokenNeverMatchesAnotherAbsentToken() {
        ClaimKeys noTokens = new ClaimKeys("CLM-NEW", "VEH-1", null, null, null, INCIDENT);
        HistoricalClaim withoutTokens = row("CLM-N", 1); // policy, claimant and garage are null here too

        HistoryMatcher.Matched matched = HistoryMatcher.match(noTokens, List.of(withoutTokens), 50, IST);

        assertThat(matched.matches()).isEmpty();
    }

    // ------------------------------------------------------------ timing

    @Test
    void timingSaysEarlierLaterOrSameDayWithTheNumberOfDays() {
        HistoricalClaim earlier = vehicle("CLM-E");
        earlier.setIncidentAt(Instant.parse("2025-04-14T05:30:00Z")); // 30 days before
        HistoricalClaim later = vehicle("CLM-L");
        later.setIncidentAt(Instant.parse("2025-05-24T05:30:00Z")); // 10 days after
        HistoricalClaim same = vehicle("CLM-S");
        same.setIncidentAt(Instant.parse("2025-05-14T12:00:00Z"));

        List<HistoricalMatch> matches = match(earlier, later, same).matches();

        assertThat(matches).extracting(HistoricalMatch::externalClaimId, HistoricalMatch::timing, HistoricalMatch::daysBetween)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("CLM-S", Timing.SAME_DAY, 0L), org.assertj.core.groups.Tuple.tuple("CLM-L", Timing.LATER, 10L),
                        org.assertj.core.groups.Tuple.tuple("CLM-E", Timing.EARLIER, 30L));
        assertThat(matches.get(0).ambiguities()).contains("ORDER_UNKNOWN_SAME_DAY");
    }

    @Test
    void calendarDaysAreTakenInTheDocumentZoneNotUtc() {
        // 20:00 UTC on 13 May is 01:30 on 14 May in India: the same calendar day as the claim's 11:00 India incident.
        HistoricalClaim lateUtc = vehicle("CLM-Z");
        lateUtc.setIncidentAt(Instant.parse("2025-05-13T20:00:00Z"));

        HistoricalMatch m = match(lateUtc).matches().get(0);

        assertThat(m.timing()).isEqualTo(Timing.SAME_DAY);
        assertThat(m.daysBetween()).isZero();
    }

    // ------------------------------------------------------------ order, cap, honesty about gaps

    @Test
    void exactMatchesComeBeforeContextThenTheClosestInTimeThenByIdSoTheOrderIsStable() {
        HistoricalClaim farExact = vehicle("CLM-B");
        farExact.setIncidentAt(Instant.parse("2024-05-14T05:30:00Z"));
        HistoricalClaim nearExact = vehicle("CLM-C");
        nearExact.setIncidentAt(Instant.parse("2025-05-01T05:30:00Z"));
        HistoricalClaim sameDistanceExact = vehicle("CLM-A");
        sameDistanceExact.setIncidentAt(Instant.parse("2025-05-27T05:30:00Z")); // 13 days after, as CLM-C is 13 before
        HistoricalClaim nearContext = row("CLM-D", 1);
        nearContext.setPolicyToken("POL-1");
        nearContext.setIncidentAt(Instant.parse("2025-05-13T05:30:00Z"));

        List<String> forward = match(farExact, nearExact, sameDistanceExact, nearContext).matches().stream().map(HistoricalMatch::externalClaimId).toList();
        List<String> reversed = match(nearContext, sameDistanceExact, nearExact, farExact).matches().stream().map(HistoricalMatch::externalClaimId).toList();

        assertThat(forward).containsExactly("CLM-A", "CLM-C", "CLM-B", "CLM-D");
        assertThat(reversed).isEqualTo(forward);
    }

    @Test
    void onlyTheClosestAreListedButTheTotalIsStillReported() {
        List<HistoricalClaim> rows = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            HistoricalClaim claim = vehicle("CLM-" + String.format("%03d", i));
            claim.setIncidentAt(INCIDENT.minusSeconds(86400L * (i + 1)));
            rows.add(claim);
        }

        HistoryMatcher.Matched matched = HistoryMatcher.match(CLAIM, rows, 50, IST);

        assertThat(matched.matches()).hasSize(50);
        assertThat(matched.totalPriorClaims()).isEqualTo(60);
        assertThat(matched.truncated()).isTrue();
        assertThat(matched.matches().get(0).externalClaimId()).isEqualTo("CLM-000");
        assertThat(matched.matches().get(49).externalClaimId()).isEqualTo("CLM-049");
    }

    @Test
    void anUnknownOutcomeIsFlaggedAsNotRecordedNeverAsCleared() {
        HistoricalClaim noOutcome = vehicle("CLM-A");
        HistoricalClaim withOutcome = vehicle("CLM-B");
        withOutcome.setOutcomeStatus("NO_ACTION");

        List<HistoricalMatch> matches = match(noOutcome, withOutcome).matches();

        assertThat(matches.stream().filter(m -> m.externalClaimId().equals("CLM-A")).findFirst().orElseThrow().ambiguities()).contains("OUTCOME_NOT_RECORDED");
        assertThat(matches.stream().filter(m -> m.externalClaimId().equals("CLM-B")).findFirst().orElseThrow().ambiguities()).doesNotContain("OUTCOME_NOT_RECORDED");
        assertThat(matches.stream().filter(m -> m.externalClaimId().equals("CLM-A")).findFirst().orElseThrow().outcomeStatus()).isNull();
    }

    @Test
    void everyMatchCarriesItsSourceRecord() {
        HistoricalClaim claim = vehicle("CLM-A");
        claim.setRowNumber(17);

        HistoricalMatch m = match(claim).matches().get(0);

        assertThat(m.source().importId()).isEqualTo(claim.getImportId());
        assertThat(m.source().rowNumber()).isEqualTo(17);
        assertThat(m.source().sourceRecordId()).isEqualTo(claim.getSourceRecordId());
    }

    @Test
    void nothingMatchingGivesAnEmptyResultNotAnError() {
        HistoryMatcher.Matched matched = match(row("CLM-X", 1), row("CLM-Y", 1));

        assertThat(matched.matches()).isEmpty();
        assertThat(matched.totalPriorClaims()).isZero();
        assertThat(matched.truncated()).isFalse();
        assertThat(matched.excludedAsSameClaim()).isZero();
    }

    @Test
    void explanationsAreNeutralAndNeverCallAnythingFraudOrSuspicious() {
        HistoricalClaim vehicle = vehicle("CLM-A");
        HistoricalClaim policy = row("CLM-P", 1);
        policy.setPolicyToken("POL-1");
        HistoricalClaim claimant = row("CLM-C", 1);
        claimant.setClaimantToken("CLT-1");

        List<HistoricalMatch> matches = match(vehicle, policy, claimant).matches();

        assertThat(matches).extracting(HistoricalMatch::explanation)
                .allSatisfy(text -> assertThat(text.toLowerCase()).doesNotContain("fraud", "suspicious", "suspect", "guilty", "proof of", "repeat offender"));
        assertThat(matches.stream().filter(m -> m.relation() == Relation.SAME_VEHICLE).findFirst().orElseThrow().explanation()).contains("not a finding by itself");
    }
}
