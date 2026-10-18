package com.example.ClaimShieldX.investigation;

import com.example.ClaimShieldX.investigation.model.Stage;
import com.example.ClaimShieldX.investigation.work.InvestigationStore;
import com.example.ClaimShieldX.investigation.work.InvestigationStore.Claimed;
import com.example.ClaimShieldX.support.TestActors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** The claiming rules of the queue itself: one worker per investigation, leases, crash recovery and the order of work. */
class InvestigationQueueTests extends InvestigationQueueTestBase {

    private Optional<Claimed> claim(String owner) {
        Instant now = clock.instant();
        return store.claimNext(owner, now, now.plus(props.lease()));
    }

    @Test
    void theOldestDueInvestigationIsClaimedFirstAndEachIsClaimedOnlyOnce() {
        TestActors actor = investigator();
        UUID first = queue(actor, claimFor(actor, "CLM-1"));
        clock.advance(Duration.ofSeconds(5));
        UUID second = queue(actor, claimFor(actor, "CLM-2"));

        assertThat(claim("w1")).get().extracting(Claimed::id).isEqualTo(first);
        assertThat(claim("w2")).get().extracting(Claimed::id).isEqualTo(second);
        assertThat(claim("w3")).isEmpty();
    }

    @Test
    void aClaimedInvestigationHoldsALeaseForTheConfiguredTime() {
        TestActors actor = investigator();
        UUID id = queue(actor, claimFor(actor, "CLM-1"));

        Claimed claimed = claim("w1").orElseThrow();

        assertThat(claimed.id()).isEqualTo(id);
        assertThat(leaseOwner(id)).isEqualTo("w1");
        Instant expires = jdbc.queryForObject("select lease_expires_at from investigations where id = ?", java.sql.Timestamp.class, id).toInstant();
        assertThat(expires).isEqualTo(clock.instant().plus(props.lease()));
        assertThat(jdbc.queryForObject("select started_at is not null from investigations where id = ?", Boolean.class, id)).isTrue();
    }

    @Test
    void anotherWorkerCannotTakeAnInvestigationWhileItsLeaseIsAlive() {
        TestActors actor = investigator();
        UUID id = queue(actor, claimFor(actor, "CLM-1"));
        claim("w1");

        clock.advance(props.lease().minusSeconds(1));

        assertThat(claim("w2")).isEmpty();
        assertThat(leaseOwner(id)).isEqualTo("w1");
    }

    @Test
    void anExpiredLeaseOnAnInvestigationNotYetStartedCanBeTakenWithoutCountingAnAttempt() {
        TestActors actor = investigator();
        UUID id = queue(actor, claimFor(actor, "CLM-1"));
        claim("w1");

        clock.advance(props.lease().plusSeconds(1));
        Claimed second = claim("w2").orElseThrow();

        assertThat(second.id()).isEqualTo(id);
        assertThat(leaseOwner(id)).isEqualTo("w2");
        assertThat(attempt(id)).isEqualTo(1);
    }

    @Test
    void anInvestigationAbandonedInAStageByACrashedWorkerIsTakenAgainAndCountsAsAnAttempt() {
        TestActors actor = investigator();
        UUID id = queue(actor, claimFor(actor, "CLM-1"));
        Claimed claimed = claim("crashed").orElseThrow();
        store.beginStage(id, "crashed", Stage.EXTRACTING, clock.instant());
        assertThat(claimed.attempt()).isEqualTo(1);

        clock.advance(props.lease().plusSeconds(1));
        Claimed recovered = claim("rescuer").orElseThrow();

        assertThat(recovered.id()).isEqualTo(id);
        assertThat(recovered.attempt()).isEqualTo(2);
        assertThat(status(id)).isEqualTo("EXTRACTING");
        assertThat(leaseOwner(id)).isEqualTo("rescuer");
    }

    @Test
    void theCrashedWorkersLateWritesAreIgnoredOnceSomeoneElseOwnsTheLease() {
        TestActors actor = investigator();
        UUID id = queue(actor, claimFor(actor, "CLM-1"));
        claim("slow");
        store.beginStage(id, "slow", Stage.EXTRACTING, clock.instant());
        clock.advance(props.lease().plusSeconds(1));
        claim("rescuer");

        boolean finished = store.finishStage(id, "slow", Stage.EXTRACTING, com.example.ClaimShieldX.investigation.model.StageStatus.OK, null, java.util.Map.of(), null, clock.instant());
        boolean completed = store.complete(id, "slow", clock.instant());
        boolean failed = store.fail(id, "slow", Stage.EXTRACTING, com.example.ClaimShieldX.investigation.model.InvestigationErrorCode.INTERNAL_ERROR, java.util.Map.of(), clock.instant());

        assertThat(finished).isFalse();
        assertThat(completed).isFalse();
        assertThat(failed).isFalse();
        assertThat(status(id)).isEqualTo("EXTRACTING");
        assertThat(stageStatus(id, Stage.EXTRACTING)).isEqualTo("RUNNING");
        assertThat(leaseOwner(id)).isEqualTo("rescuer");
    }

    @Test
    void theHeartbeatExtendsOnlyTheLeasesOfTheWorkerThatBeats() {
        TestActors actor = investigator();
        UUID mine = queue(actor, claimFor(actor, "CLM-1"));
        clock.advance(Duration.ofSeconds(1));      // different creation times, so w1 certainly takes the older one
        UUID theirs = queue(actor, claimFor(actor, "CLM-2"));
        claim("w1");
        claim("w2");

        clock.advance(Duration.ofMinutes(2));
        int renewed = store.heartbeat("w1", clock.instant().plus(props.lease()));
        clock.advance(Duration.ofMinutes(2));

        assertThat(renewed).isEqualTo(1);
        assertThat(store.holdsLease(mine, "w1")).isTrue();
        // w2 never beat: its lease ran out after 3 minutes and w3 can take it, but not w1's.
        assertThat(claim("w3")).get().extracting(Claimed::id).isEqualTo(theirs);
        assertThat(claim("w3")).isEmpty();
    }

    @Test
    void aFinishedInvestigationIsNeverClaimed() {
        TestActors actor = investigator();
        UUID id = queue(actor, claimFor(actor, "CLM-1"));
        claim("w1");
        store.complete(id, "w1", clock.instant());

        clock.advance(Duration.ofDays(1));

        assertThat(claim("w2")).isEmpty();
        assertThat(status(id)).isEqualTo("COMPLETED");
        assertThat(leaseOwner(id)).isNull();
    }

    @Test
    void anInvestigationSleepingBeforeARetryIsNotClaimedUntilItIsDue() {
        TestActors actor = investigator();
        UUID id = queue(actor, claimFor(actor, "CLM-1"));
        claim("w1");
        store.beginStage(id, "w1", Stage.EXTRACTING, clock.instant());
        Instant due = clock.instant().plusSeconds(30);
        store.scheduleRetry(id, "w1", Stage.EXTRACTING, com.example.ClaimShieldX.investigation.model.InvestigationErrorCode.MODEL_UNAVAILABLE, java.util.Map.of(), due, clock.instant());

        assertThat(status(id)).isEqualTo("RETRY_WAIT");
        assertThat(attempt(id)).isEqualTo(2);
        assertThat(nextAttemptAt(id)).isEqualTo(due);
        assertThat(leaseOwner(id)).isNull();
        assertThat(claim("w2")).isEmpty();
        clock.advance(Duration.ofSeconds(29));
        assertThat(claim("w2")).isEmpty();
        clock.advance(Duration.ofSeconds(1));
        assertThat(claim("w2")).get().extracting(Claimed::id).isEqualTo(id);
        // A retry wake-up is not a crash: the attempt number set when the retry was scheduled stays.
        assertThat(attempt(id)).isEqualTo(2);
    }

    @Test
    @Timeout(120)
    void manyWorkersClaimingAtTheSameMomentNeverTakeTheSameInvestigationTwice() throws Exception {
        TestActors actor = investigator();
        Set<UUID> queued = new HashSet<>();
        for (int i = 0; i < 24; i++) {
            queued.add(queue(actor, claimFor(actor, "CLM-" + i)));
        }
        int workers = 8;
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        CountDownLatch go = new CountDownLatch(1);
        List<UUID> claimedIds = Collections.synchronizedList(new ArrayList<>());
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int w = 0; w < workers; w++) {
                String owner = "w" + w;
                futures.add(pool.submit(() -> {
                    go.await();
                    Optional<Claimed> next;
                    while ((next = claim(owner)).isPresent()) {
                        claimedIds.add(next.get().id());
                    }
                    return null;
                }));
            }
            go.countDown();
            for (Future<?> future : futures) {
                future.get(90, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(claimedIds).hasSize(24).doesNotHaveDuplicates();
        assertThat(new HashSet<>(claimedIds)).isEqualTo(queued);
    }

    @Test
    void waitingInvestigationsAreClaimedOnlyWhenTheirRecheckIsDueOrAnUploadWakesThem() {
        TestActors actor = investigator();
        UUID id = queue(actor, claimFor(actor, "CLM-1"));
        UUID claimId = jdbc.queryForObject("select claim_id from investigations where id = ?", UUID.class, id);
        claim("w1");
        store.beginStage(id, "w1", Stage.PREPARING_EVIDENCE, clock.instant());
        store.waitForEvidence(id, "w1", java.util.Map.of("blockers", List.of()), clock.instant().plusSeconds(15), clock.instant());

        assertThat(status(id)).isEqualTo("WAITING_FOR_EVIDENCE");
        assertThat(claim("w2")).isEmpty();
        assertThat(store.wakeWaiting(actor.organizationId(), claimId, clock.instant())).isEqualTo(1);
        assertThat(claim("w2")).get().extracting(Claimed::id).isEqualTo(id);
    }

    @Test
    void anUploadOnAnotherClaimOrOrganizationDoesNotWakeIt() {
        TestActors actor = investigator();
        TestActors stranger = investigator();
        UUID id = queue(actor, claimFor(actor, "CLM-1"));
        UUID claimId = jdbc.queryForObject("select claim_id from investigations where id = ?", UUID.class, id);
        claim("w1");
        store.beginStage(id, "w1", Stage.PREPARING_EVIDENCE, clock.instant());
        store.waitForEvidence(id, "w1", java.util.Map.of(), clock.instant().plusSeconds(15), clock.instant());

        assertThat(store.wakeWaiting(actor.organizationId(), UUID.randomUUID(), clock.instant())).isZero();
        assertThat(store.wakeWaiting(stranger.organizationId(), claimId, clock.instant())).isZero();
        assertThat(claim("w2")).isEmpty();
    }
}
