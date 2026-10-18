package com.example.ClaimShieldX.investigation;

import com.example.ClaimShieldX.support.TestActors;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InvestigationApiTests extends InvestigationApiTestBase {

    // ---------------------------------------------------------------- creating

    @Test
    void anInvestigationIsQueuedDurablyAndTheAnswerPointsAtWhereToPoll() throws Exception {
        TestActors actor = actor();
        UUID claim = claimWithKnownFacts(actor);

        String body = createInvestigation(actor, claim).andExpect(status().isAccepted())
                .andExpect(header().string("Location", startsWith("/api/v1/investigations/")))
                .andExpect(jsonPath("$.claimId").value(claim.toString()))
                .andExpect(jsonPath("$.claimVersion").value(1))
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.attempt").value(1))
                .andExpect(jsonPath("$.maxAttempts").value(3))
                .andExpect(jsonPath("$.nextRetryAt").doesNotExist())
                .andExpect(jsonPath("$.snapshotId").doesNotExist())
                .andExpect(jsonPath("$.reportStatus").doesNotExist())
                .andExpect(jsonPath("$.error").doesNotExist())
                .andExpect(jsonPath("$.cancelRequested").value(false))
                .andExpect(jsonPath("$.stages", hasSize(6)))
                .andExpect(jsonPath("$.stages[*].status").value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("PENDING"))))
                .andReturn().getResponse().getContentAsString();

        UUID id = UUID.fromString(JsonPath.read(body, "$.id"));
        assertThat(JsonPath.<java.util.List<String>>read(body, "$.stages[*].stage")).containsExactly("PREPARING_EVIDENCE", "EXTRACTING", "RUNNING_RULES",
                "BUILDING_HYPOTHESES", "VALIDATING_FINDINGS", "GENERATING_REPORT");
        // Committed before the answer: a second connection (this one) already sees the row.
        assertThat(jdbc.queryForObject("select count(*) from investigations where id = ? and status = 'QUEUED' and requested_by = ?", Integer.class, id, actor.userId())).isEqualTo(1);
    }

    @Test
    void theNoteIsKeptAndTooLongANoteIsRefused() throws Exception {
        TestActors actor = actor();
        UUID claim = claimWithKnownFacts(actor);

        createInvestigation(actor, claim, newKey(), "{\"note\":\"  check the bumper again  \"}").andExpect(status().isAccepted()).andExpect(jsonPath("$.note").value("check the bumper again"));

        UUID other = claimWithKnownFacts(actor);
        createInvestigation(actor, other, newKey(), "{\"note\":\"" + "x".repeat(501) + "\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        assertThat(jdbc.queryForObject("select count(*) from investigations where claim_id = ?", Integer.class, other)).isZero();
    }

    @Test
    void theIdempotencyKeyIsRequiredAndMustBeWellFormed() throws Exception {
        TestActors actor = actor();
        UUID claim = claimWithKnownFacts(actor);

        createInvestigation(actor, claim, null, null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
        createInvestigation(actor, claim, "short", null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        createInvestigation(actor, claim, "has spaces in the key", null).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select count(*) from investigations where claim_id = ?", Integer.class, claim)).isZero();
    }

    @Test
    void repeatingTheSameRequestWithTheSameKeyReturnsTheOriginalInvestigation() throws Exception {
        TestActors actor = actor();
        UUID claim = claimWithKnownFacts(actor);
        String key = newKey();
        String first = createInvestigation(actor, claim, key, null).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();

        createInvestigation(actor, claim, key, null).andExpect(status().isAccepted()).andExpect(header().string("Idempotent-Replay", "true"))
                .andExpect(jsonPath("$.id").value(JsonPath.<String>read(first, "$.id")));

        assertThat(jdbc.queryForObject("select count(*) from investigations where claim_id = ?", Integer.class, claim)).isEqualTo(1);
    }

    @Test
    void theSameKeyForADifferentRequestIsRefused() throws Exception {
        TestActors actor = actor();
        UUID claim = claimWithKnownFacts(actor);
        UUID other = claimWithKnownFacts(actor);
        String key = newKey();
        createInvestigation(actor, claim, key, null).andExpect(status().isAccepted());

        createInvestigation(actor, other, key, null).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        createInvestigation(actor, claim, key, "{\"note\":\"different\"}").andExpect(status().isUnprocessableEntity());
    }

    @Test
    void aSecondInvestigationWhileOneIsActiveIsRefusedAndNamesTheActiveOne() throws Exception {
        TestActors actor = actor();
        UUID claim = claimWithKnownFacts(actor);
        UUID active = createOk(actor, claim);

        createInvestigation(actor, claim).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVESTIGATION_ALREADY_ACTIVE"))
                .andExpect(jsonPath("$.details.activeInvestigationId").value(active.toString()));

        assertThat(jdbc.queryForObject("select count(*) from investigations where claim_id = ?", Integer.class, claim)).isEqualTo(1);
    }

    @Test
    void aFailedKeyIsReleasedSoTheSameRequestMayBeTriedAgainOnceTheActiveOneIsGone() throws Exception {
        TestActors actor = actor();
        UUID claim = claimWithKnownFacts(actor);
        UUID active = createOk(actor, claim);
        String key = newKey();
        createInvestigation(actor, claim, key, null).andExpect(status().isConflict());

        postAction(actor, active, "cancel", newKey()).andExpect(status().isOk());
        createInvestigation(actor, claim, key, null).andExpect(status().isAccepted()).andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.not(active.toString())));
    }

    @Test
    void anOrganizationMayStartOnlyTheConfiguredNumberOfInvestigationsInADay() throws Exception {
        TestActors actor = actor();
        TestActors elsewhere = actor();
        UUID history = claimWithKnownFacts(actor);
        Timestamp now = Timestamp.from(Instant.now());
        for (int i = 0; i < 20; i++) {
            jdbc.update("""
                    insert into investigations(id, created_at, updated_at, organization_id, claim_id, claim_version, requested_by, status, attempt, max_attempts, finished_at)
                    values (?, ?, ?, ?, ?, 1, ?, 'COMPLETED', 1, 3, ?)
                    """, UUID.randomUUID(), now, now, actor.organizationId(), history, actor.userId(), now);
        }
        UUID claim = claimWithKnownFacts(actor);

        createInvestigation(actor, claim).andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("INVESTIGATION_QUOTA_EXCEEDED"));
        assertThat(jdbc.queryForObject("select count(*) from investigations where claim_id = ?", Integer.class, claim)).isZero();
        createInvestigation(elsewhere, claimWithKnownFacts(elsewhere)).andExpect(status().isAccepted());
    }

    @Test
    void investigationsOlderThanADayDoNotCountTowardsTheLimit() throws Exception {
        TestActors actor = actor();
        UUID history = claimWithKnownFacts(actor);
        Timestamp old = Timestamp.from(Instant.now().minusSeconds(25 * 3600));
        for (int i = 0; i < 25; i++) {
            jdbc.update("""
                    insert into investigations(id, created_at, updated_at, organization_id, claim_id, claim_version, requested_by, status, attempt, max_attempts, finished_at)
                    values (?, ?, ?, ?, ?, 1, ?, 'COMPLETED', 1, 3, ?)
                    """, UUID.randomUUID(), old, old, actor.organizationId(), history, actor.userId(), old);
        }

        createInvestigation(actor, claimWithKnownFacts(actor)).andExpect(status().isAccepted());
    }

    // ---------------------------------------------------------------- who may do what

    @Test
    void anotherOrganizationsClaimAndInvestigationLookLikeTheyDoNotExist() throws Exception {
        TestActors owner = actor();
        TestActors stranger = actor();
        UUID claim = claimWithKnownFacts(owner);
        UUID id = createOk(owner, claim);

        createInvestigation(stranger, claim).andExpect(status().isNotFound());
        getInvestigation(stranger, id).andExpect(status().isNotFound());
        listInvestigations(stranger, claim).andExpect(status().isNotFound());
        postAction(stranger, id, "cancel", newKey()).andExpect(status().isNotFound());
        postAction(stranger, id, "retry", newKey()).andExpect(status().isNotFound());
        assertThat(investigationStatus(id)).isEqualTo("QUEUED");
    }

    @Test
    void unknownIdsAreNotFoundAndNoTokenIsRefused() throws Exception {
        TestActors actor = actor();
        UUID claim = claimWithKnownFacts(actor);

        createInvestigation(actor, UUID.randomUUID()).andExpect(status().isNotFound());
        getInvestigation(actor, UUID.randomUUID()).andExpect(status().isNotFound());
        postAction(actor, UUID.randomUUID(), "cancel", newKey()).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/claims/" + claim + "/investigations").header("Idempotency-Key", newKey())).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/investigations/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    @Test
    void investigatorsAndAdministratorsMayBothQueueAndRead() throws Exception {
        TestActors investigator = actor("INVESTIGATOR");
        TestActors admin = TestActors.inOrganization(fixtures, jwtSecret, investigator.organizationId(), "ORG_ADMIN");
        UUID claim = claimWithKnownFacts(investigator);
        UUID id = createOk(admin, claim);

        getInvestigation(investigator, id).andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- reading and listing

    @Test
    void theListIsPagedNewestFirstAndValidated() throws Exception {
        TestActors actor = actor();
        UUID claim = claimWithKnownFacts(actor);
        UUID first = createOk(actor, claim);
        postAction(actor, first, "cancel", newKey()).andExpect(status().isOk());
        UUID second = createOk(actor, claim);

        listInvestigations(actor, claim).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.items[0].id").value(second.toString())).andExpect(jsonPath("$.items[1].id").value(first.toString()))
                .andExpect(jsonPath("$.items[0].stages", hasSize(6)));
        listInvestigations(actor, claim, "size", "1", "page", "1").andExpect(jsonPath("$.items", hasSize(1))).andExpect(jsonPath("$.items[0].id").value(first.toString()));
        listInvestigations(actor, claim, "size", "0").andExpect(status().isBadRequest());
        listInvestigations(actor, claim, "sort", "secret,asc").andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- cancel

    @Test
    void cancellingAQueuedInvestigationAnswersWithItCancelledAndNothingWorksOnItAfterwards() throws Exception {
        TestActors actor = actor();
        UUID claim = claimWithKnownFacts(actor);
        UUID id = createOk(actor, claim);

        postAction(actor, id, "cancel", newKey()).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED")).andExpect(jsonPath("$.cancelRequested").value(false));

        assertThat(drain()).isZero();
        assertThat(investigationStatus(id)).isEqualTo("CANCELLED");
    }

    @Test
    void cancellingAWorkingInvestigationAsksItToStopAndSaysSo() throws Exception {
        TestActors actor = actor();
        UUID claim = claimWithKnownFacts(actor);
        UUID id = createOk(actor, claim);
        // A worker holds it and is in the middle of a stage.
        jdbc.update("update investigations set status = 'EXTRACTING', lease_owner = 'busy-worker', lease_expires_at = ? where id = ?", Timestamp.from(Instant.now().plusSeconds(120)), id);

        postAction(actor, id, "cancel", newKey()).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("EXTRACTING")).andExpect(jsonPath("$.cancelRequested").value(true));
    }

    @Test
    void aFinishedInvestigationCannotBeCancelled() throws Exception {
        TestActors actor = actor();
        UUID claim = claimWithKnownFacts(actor);
        UUID id = createOk(actor, claim);
        postAction(actor, id, "cancel", newKey()).andExpect(status().isOk());

        postAction(actor, id, "cancel", newKey()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    void cancelAndRetryNeedAnIdempotencyKeyAndCancelReplaysCleanly() throws Exception {
        TestActors actor = actor();
        UUID claim = claimWithKnownFacts(actor);
        UUID id = createOk(actor, claim);
        String key = newKey();

        postAction(actor, id, "cancel", null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
        postAction(actor, id, "retry", null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
        postAction(actor, id, "cancel", key).andExpect(status().isOk());
        postAction(actor, id, "cancel", key).andExpect(status().isOk()).andExpect(header().string("Idempotent-Replay", "true")).andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    // ---------------------------------------------------------------- retry

    @Test
    void onlyAFailedInvestigationCanBeRetried() throws Exception {
        TestActors actor = actor();
        UUID claim = claimWithKnownFacts(actor);
        UUID id = createOk(actor, claim);

        postAction(actor, id, "retry", newKey()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    void aFailedInvestigationIsQueuedAgainAndTheRetryReplaysCleanly() throws Exception {
        TestActors actor = actor();
        UUID claim = claimWithKnownFacts(actor);
        UUID id = createOk(actor, claim);
        jdbc.update("update investigations set status = 'FAILED', error_code = 'INTERNAL_ERROR', error_message = 'x', finished_at = now() where id = ?", id);
        String key = newKey();

        postAction(actor, id, "retry", key).andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("QUEUED")).andExpect(jsonPath("$.error").doesNotExist())
                .andExpect(jsonPath("$.attempt").value(1));
        postAction(actor, id, "retry", key).andExpect(status().isAccepted()).andExpect(header().string("Idempotent-Replay", "true"));
    }

    @Test
    void aFailedInvestigationCannotBeRetriedWhileAnotherOneOfTheClaimIsActive() throws Exception {
        TestActors actor = actor();
        UUID claim = claimWithKnownFacts(actor);
        UUID failed = createOk(actor, claim);
        jdbc.update("update investigations set status = 'FAILED', error_code = 'INTERNAL_ERROR', error_message = 'x', finished_at = now() where id = ?", failed);
        UUID active = createOk(actor, claim);

        postAction(actor, failed, "retry", newKey()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVESTIGATION_ALREADY_ACTIVE"))
                .andExpect(jsonPath("$.details.activeInvestigationId").value(active.toString()));
        assertThat(investigationStatus(failed)).isEqualTo("FAILED");
    }
}
