package com.example.ClaimShieldX.investigation;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.ClaimShieldX.investigation.model.InvestigationErrorCode;
import com.example.ClaimShieldX.investigation.model.Stage;
import com.example.ClaimShieldX.investigation.work.InvestigationRunner;
import com.example.ClaimShieldX.investigation.work.InvestigationStore.CancelResult;
import com.example.ClaimShieldX.investigation.work.InvestigationStore.RetryResult;
import com.example.ClaimShieldX.investigation.work.StageOutcome;
import com.example.ClaimShieldX.support.TestActors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A hang is a failure, not a stuck build: no test here may take longer than a minute.
 *  The lifecycle of one investigation: stages in order, retries with backoff, waiting, cancelling, retrying, and what a crash leaves behind. */
@Timeout(60)
class InvestigationRunnerTests extends InvestigationQueueTestBase {

    private static final String WORKER = "worker-1";

    private UUID newInvestigation() {
        TestActors actor = investigator();
        return queue(actor, claimFor(actor, "CLM-" + UUID.randomUUID()));
    }

    // ---------------------------------------------------------------- the normal path

    @Test
    void anInvestigationRunsEveryStageInOrderAndCompletesWithItsStagesRecorded() {
        UUID id = newInvestigation();
        List<Stage> order = new ArrayList<>();
        ScriptedHandler prepare = new ScriptedHandler(Stage.PREPARING_EVIDENCE).thenDo(c -> {
            order.add(Stage.PREPARING_EVIDENCE);
            return StageOutcome.ok(Map.of("readiness", "READY"));
        });
        ScriptedHandler extract = new ScriptedHandler(Stage.EXTRACTING).thenDo(c -> {
            order.add(Stage.EXTRACTING);
            return StageOutcome.degraded(Map.of("failed", 1));
        });
        ScriptedHandler rules = new ScriptedHandler(Stage.RUNNING_RULES).thenDo(c -> {
            order.add(Stage.RUNNING_RULES);
            return StageOutcome.ok(Map.of());
        });

        boolean worked = runner(prepare, extract, rules).runNext(WORKER);

        assertThat(worked).isTrue();
        assertThat(order).containsExactly(Stage.PREPARING_EVIDENCE, Stage.EXTRACTING, Stage.RUNNING_RULES);
        assertThat(status(id)).isEqualTo("COMPLETED");
        assertThat(leaseOwner(id)).isNull();
        assertThat(jdbc.queryForObject("select finished_at is not null from investigations where id = ?", Boolean.class, id)).isTrue();
        assertThat(stageStatus(id, Stage.PREPARING_EVIDENCE)).isEqualTo("OK");
        assertThat(stageStatus(id, Stage.EXTRACTING)).isEqualTo("DEGRADED");
        assertThat(stageStatus(id, Stage.RUNNING_RULES)).isEqualTo("OK");
        assertThat(stageStatus(id, Stage.BUILDING_HYPOTHESES)).isEqualTo("SKIPPED");
        assertThat(stageStatus(id, Stage.GENERATING_REPORT)).isEqualTo("SKIPPED");
        assertThat(jdbc.queryForObject("select summary ->> 'readiness' from investigation_stages where investigation_id = ? and stage = 'PREPARING_EVIDENCE'", String.class, id)).isEqualTo("READY");
        assertThat(runner().runNext(WORKER)).isFalse();
    }

    @Test
    void theContextTellsAHandlerWhichInvestigationAndAttemptItIsWorkingOn() {
        TestActors actor = investigator();
        UUID claim = claimFor(actor, "CLM-CTX");
        UUID id = queue(actor, claim);
        ScriptedHandler prepare = new ScriptedHandler(Stage.PREPARING_EVIDENCE);

        runner(prepare).runNext(WORKER);

        assertThat(prepare.contexts()).singleElement().satisfies(c -> {
            assertThat(c.investigationId()).isEqualTo(id);
            assertThat(c.claimId()).isEqualTo(claim);
            assertThat(c.organizationId()).isEqualTo(actor.organizationId());
            assertThat(c.requestedBy()).isEqualTo(actor.userId());
            assertThat(c.attempt()).isEqualTo(1);
            assertThat(c.maxAttempts()).isEqualTo(3);
        });
    }

    // ---------------------------------------------------------------- transient failures

    @Test
    void aTransientFailureSleepsWithBackoffThenResumesAtTheFailedStageWithoutRepeatingEarlierOnes() {
        UUID id = newInvestigation();
        ScriptedHandler prepare = new ScriptedHandler(Stage.PREPARING_EVIDENCE);
        ScriptedHandler extract = new ScriptedHandler(Stage.EXTRACTING).then(new StageOutcome.Transient(InvestigationErrorCode.MODEL_UNAVAILABLE, Map.of("willRetry", 1)));
        InvestigationRunner runner = runner(prepare, extract);

        runner.runNext(WORKER);

        assertThat(status(id)).isEqualTo("RETRY_WAIT");
        assertThat(attempt(id)).isEqualTo(2);
        assertThat(nextAttemptAt(id)).isEqualTo(clock.instant().plusSeconds(30));
        assertThat(errorCode(id)).isEqualTo("MODEL_UNAVAILABLE");
        assertThat(jdbc.queryForObject("select error_message from investigations where id = ?", String.class, id)).isEqualTo(InvestigationErrorCode.MODEL_UNAVAILABLE.message());
        assertThat(leaseOwner(id)).isNull();
        assertThat(stageStatus(id, Stage.PREPARING_EVIDENCE)).isEqualTo("OK");
        assertThat(stageStatus(id, Stage.EXTRACTING)).isEqualTo("PENDING");

        assertThat(runner.runNext(WORKER)).as("not due yet").isFalse();
        clock.advance(Duration.ofSeconds(29));
        assertThat(runner.runNext(WORKER)).as("one second early").isFalse();
        clock.advance(Duration.ofSeconds(1));
        assertThat(runner.runNext(WORKER)).isTrue();

        assertThat(status(id)).isEqualTo("COMPLETED");
        assertThat(prepare.calls()).as("the finished stage is not repeated").isEqualTo(1);
        assertThat(extract.calls()).isEqualTo(2);
        assertThat(extract.contexts().get(1).attempt()).isEqualTo(2);
        assertThat(errorCode(id)).as("a later success clears the old error").isNull();
    }

    @Test
    void theDelayDoublesAfterEachFailedAttempt() {
        UUID id = newInvestigation();
        ScriptedHandler rules = new ScriptedHandler(Stage.RUNNING_RULES)
                .then(new StageOutcome.Transient(InvestigationErrorCode.INTERNAL_ERROR, Map.of()))
                .then(new StageOutcome.Transient(InvestigationErrorCode.INTERNAL_ERROR, Map.of()));
        InvestigationRunner runner = runner(rules);

        runner.runNext(WORKER);
        assertThat(nextAttemptAt(id)).isEqualTo(clock.instant().plusSeconds(30));
        clock.advance(Duration.ofSeconds(30));
        runner.runNext(WORKER);

        assertThat(status(id)).isEqualTo("RETRY_WAIT");
        assertThat(attempt(id)).isEqualTo(3);
        assertThat(nextAttemptAt(id)).isEqualTo(clock.instant().plusSeconds(60));
    }

    @Test
    void whenAttemptsRunOutOnAStageThatCannotDegradeTheInvestigationFailsWithTheLastCode() {
        UUID id = newInvestigation();
        ScriptedHandler rules = new ScriptedHandler(Stage.RUNNING_RULES)
                .then(new StageOutcome.Transient(InvestigationErrorCode.INTERNAL_ERROR, Map.of()))
                .then(new StageOutcome.Transient(InvestigationErrorCode.INTERNAL_ERROR, Map.of()))
                .then(new StageOutcome.Transient(InvestigationErrorCode.STORAGE_UNAVAILABLE, Map.of("last", true)));
        InvestigationRunner runner = runner(rules);

        runner.runNext(WORKER);
        clock.advance(Duration.ofSeconds(30));
        runner.runNext(WORKER);
        clock.advance(Duration.ofSeconds(60));
        runner.runNext(WORKER);

        assertThat(rules.calls()).isEqualTo(3);
        assertThat(status(id)).isEqualTo("FAILED");
        assertThat(errorCode(id)).isEqualTo("STORAGE_UNAVAILABLE");
        assertThat(stageStatus(id, Stage.RUNNING_RULES)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("select finished_at is not null from investigations where id = ?", Boolean.class, id)).isTrue();
        assertThat(leaseOwner(id)).isNull();
        clock.advance(Duration.ofDays(1));
        assertThat(runner.runNext(WORKER)).as("a failed investigation is not picked up again by itself").isFalse();
    }

    @Test
    void whenAttemptsRunOutOnAStageThatCanDegradeTheStageIsMarkedDegradedAndTheInvestigationCarriesOn() {
        UUID id = newInvestigation();
        ScriptedHandler extract = new ScriptedHandler(Stage.EXTRACTING, true)
                .then(new StageOutcome.Transient(InvestigationErrorCode.MODEL_QUOTA_EXHAUSTED, Map.of()))
                .then(new StageOutcome.Transient(InvestigationErrorCode.MODEL_QUOTA_EXHAUSTED, Map.of()))
                .then(new StageOutcome.Transient(InvestigationErrorCode.MODEL_QUOTA_EXHAUSTED, Map.of("willRetry", 2)));
        ScriptedHandler rules = new ScriptedHandler(Stage.RUNNING_RULES);
        InvestigationRunner runner = runner(extract, rules);

        runner.runNext(WORKER);
        clock.advance(Duration.ofSeconds(30));
        runner.runNext(WORKER);
        clock.advance(Duration.ofSeconds(60));
        runner.runNext(WORKER);

        assertThat(status(id)).isEqualTo("COMPLETED");
        assertThat(stageStatus(id, Stage.EXTRACTING)).isEqualTo("DEGRADED");
        assertThat(stageStatus(id, Stage.RUNNING_RULES)).isEqualTo("OK");
        assertThat(rules.calls()).isEqualTo(1);
        Map<String, Object> stage = jdbc.queryForMap("select error_code, summary ->> 'retriesExhausted' as exhausted, summary ->> 'errorCode' as code from investigation_stages where investigation_id = ? and stage = 'EXTRACTING'", id);
        assertThat(stage).containsEntry("error_code", "MODEL_QUOTA_EXHAUSTED").containsEntry("exhausted", "true").containsEntry("code", "MODEL_QUOTA_EXHAUSTED");
    }

    @Test
    void aPermanentErrorFailsAtOnceWithoutRetrying() {
        UUID id = newInvestigation();
        ScriptedHandler rules = new ScriptedHandler(Stage.RUNNING_RULES).then(new StageOutcome.Permanent(InvestigationErrorCode.EXTRACTION_FAILED, Map.of("reason", "x")));

        runner(rules).runNext(WORKER);

        assertThat(rules.calls()).isEqualTo(1);
        assertThat(status(id)).isEqualTo("FAILED");
        assertThat(attempt(id)).isEqualTo(1);
        assertThat(errorCode(id)).isEqualTo("EXTRACTION_FAILED");
        assertThat(jdbc.queryForObject("select error_message from investigations where id = ?", String.class, id)).isEqualTo(InvestigationErrorCode.EXTRACTION_FAILED.message());
        assertThat(stageStatus(id, Stage.RUNNING_RULES)).isEqualTo("FAILED");
        assertThat(stageStatus(id, Stage.BUILDING_HYPOTHESES)).isNull();
    }

    @Test
    void anExceptionInAHandlerIsATransientInternalErrorThatNeverLeaksItsMessageIntoTheDatabaseOrTheLog() {
        UUID id = newInvestigation();
        Logger logger = (Logger) LoggerFactory.getLogger(InvestigationRunner.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            ScriptedHandler rules = new ScriptedHandler(Stage.RUNNING_RULES).thenThrow(new IllegalStateException("grand total 62510.00 for VEH-TOKEN-1001"));

            runner(rules).runNext(WORKER);

            assertThat(status(id)).isEqualTo("RETRY_WAIT");
            assertThat(errorCode(id)).isEqualTo("INTERNAL_ERROR");
            assertThat(jdbc.queryForObject("select error_message from investigations where id = ?", String.class, id)).doesNotContain("62510").doesNotContain("VEH-TOKEN");
            assertThat(appender.list).filteredOn(e -> e.getLevel() == Level.ERROR).singleElement().satisfies(e -> {
                assertThat(e.getFormattedMessage()).contains("IllegalStateException").doesNotContain("62510").doesNotContain("VEH-TOKEN");
                assertThat(e.getThrowableProxy()).isNull();
            });
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void aHandlerThatKeepsStoppingForNoReasonDoesNotLoopForEver() {
        UUID id = newInvestigation();
        ScriptedHandler prepare = new ScriptedHandler(Stage.PREPARING_EVIDENCE);
        for (int i = 0; i < 10; i++) {
            prepare.then(new StageOutcome.Interrupted());
        }

        runner(prepare).runNext(WORKER);

        assertThat(status(id)).isEqualTo("FAILED");
        assertThat(errorCode(id)).isEqualTo("INTERNAL_ERROR");
        assertThat(prepare.calls()).isEqualTo(4);
    }

    // ---------------------------------------------------------------- waiting for something held elsewhere

    @Test
    void aStageWaitingForSomethingHeldElsewhereSleepsWithoutUsingAnAttemptAndResumesWhenDue() {
        UUID id = newInvestigation();
        Instant releaseAt = clock.instant().plusSeconds(20);
        ScriptedHandler extract = new ScriptedHandler(Stage.EXTRACTING).then(new StageOutcome.Busy(releaseAt, Map.of("busy", 1)));
        InvestigationRunner runner = runner(extract);

        runner.runNext(WORKER);

        assertThat(status(id)).isEqualTo("RETRY_WAIT");
        assertThat(attempt(id)).as("nothing went wrong, so no attempt is used").isEqualTo(1);
        assertThat(errorCode(id)).isNull();
        assertThat(jdbc.queryForObject("select error_message from investigations where id = ?", String.class, id)).isNull();
        assertThat(nextAttemptAt(id)).isEqualTo(releaseAt);
        assertThat(leaseOwner(id)).isNull();
        assertThat(stageStatus(id, Stage.EXTRACTING)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("select summary ->> 'busy' from investigation_stages where investigation_id = ? and stage = 'EXTRACTING'", String.class, id)).isEqualTo("1");

        assertThat(runner.runNext(WORKER)).isFalse();
        clock.advance(Duration.ofSeconds(20));
        assertThat(runner.runNext(WORKER)).isTrue();

        assertThat(status(id)).isEqualTo("COMPLETED");
        assertThat(attempt(id)).isEqualTo(1);
        assertThat(extract.calls()).isEqualTo(2);
        assertThat(extract.contexts().get(1).attempt()).isEqualTo(1);
    }

    @Test
    void aStaleWorkerCannotPutAnInvestigationToSleepThatSomeoneElseNowOwns() {
        TestActors actor = investigator();
        UUID id = queue(actor, claimFor(actor, "CLM-B1"));
        runner().claim("slow").orElseThrow();
        store.beginStage(id, "slow", Stage.EXTRACTING, clock.instant());
        clock.advance(props.lease().plusSeconds(1));
        runner().claim("rescuer").orElseThrow();

        boolean slept = store.sleepUntil(id, "slow", Stage.EXTRACTING, Map.of(), clock.instant().plusSeconds(60), clock.instant());

        assertThat(slept).isFalse();
        assertThat(status(id)).isEqualTo("EXTRACTING");
        assertThat(leaseOwner(id)).isEqualTo("rescuer");
    }

    // ---------------------------------------------------------------- waiting for evidence

    @Test
    void aMissingGatingDocumentMakesTheInvestigationWaitAndItStartsWhenTheDocumentArrives() {
        TestActors actor = investigator();
        UUID claim = claimFor(actor, "CLM-WAIT");
        UUID id = queue(actor, claim);
        ScriptedHandler prepare = new ScriptedHandler(Stage.PREPARING_EVIDENCE)
                .then(new StageOutcome.Waiting(Map.of("blockers", List.of(Map.of("documentType", "FINAL_INVOICE", "code", "DOCUMENT_MISSING")))));
        ScriptedHandler rules = new ScriptedHandler(Stage.RUNNING_RULES);
        InvestigationRunner runner = runner(prepare, rules);

        runner.runNext(WORKER);

        assertThat(status(id)).isEqualTo("WAITING_FOR_EVIDENCE");
        assertThat(nextAttemptAt(id)).isEqualTo(clock.instant().plus(props.waitingRecheck()));
        assertThat(leaseOwner(id)).isNull();
        assertThat(stageStatus(id, Stage.PREPARING_EVIDENCE)).isEqualTo("PENDING");
        assertThat(attempt(id)).as("waiting is not a failure and uses no attempt").isEqualTo(1);
        assertThat(runner.runNext(WORKER)).isFalse();

        store.wakeWaiting(actor.organizationId(), claim, clock.instant());
        assertThat(runner.runNext(WORKER)).isTrue();

        assertThat(status(id)).isEqualTo("COMPLETED");
        assertThat(prepare.calls()).isEqualTo(2);
        assertThat(rules.calls()).isEqualTo(1);
    }

    @Test
    void aWaitingInvestigationLooksAgainAtItsScheduledRecheckAndWaitsAgainIfNothingChanged() {
        UUID id = newInvestigation();
        ScriptedHandler prepare = new ScriptedHandler(Stage.PREPARING_EVIDENCE)
                .then(new StageOutcome.Waiting(Map.of()))
                .then(new StageOutcome.Waiting(Map.of()));
        InvestigationRunner runner = runner(prepare);

        runner.runNext(WORKER);
        clock.advance(props.waitingRecheck());
        assertThat(runner.runNext(WORKER)).isTrue();

        assertThat(status(id)).isEqualTo("WAITING_FOR_EVIDENCE");
        assertThat(nextAttemptAt(id)).isEqualTo(clock.instant().plus(props.waitingRecheck()));
        assertThat(prepare.calls()).isEqualTo(2);
    }

    // ---------------------------------------------------------------- cancelling

    @Test
    void aQueuedInvestigationIsCancelledAtOnceAndNeverRuns() {
        TestActors actor = investigator();
        UUID id = queue(actor, claimFor(actor, "CLM-C1"));
        ScriptedHandler prepare = new ScriptedHandler(Stage.PREPARING_EVIDENCE);

        assertThat(store.requestCancel(actor.organizationId(), id, clock.instant())).isEqualTo(CancelResult.CANCELLED);

        assertThat(status(id)).isEqualTo("CANCELLED");
        assertThat(runner(prepare).runNext(WORKER)).isFalse();
        assertThat(prepare.calls()).isZero();
    }

    @Test
    void anInvestigationAWorkerHasJustClaimedIsAskedToStopNotCancelledUnderIt() {
        TestActors actor = investigator();
        UUID id = queue(actor, claimFor(actor, "CLM-C0"));
        var claimed = runner().claim(WORKER).orElseThrow();      // still QUEUED, but a worker holds it

        assertThat(store.requestCancel(actor.organizationId(), id, clock.instant())).isEqualTo(CancelResult.CANCEL_REQUESTED);

        assertThat(status(id)).isEqualTo("QUEUED");
        ScriptedHandler prepare = new ScriptedHandler(Stage.PREPARING_EVIDENCE);
        runner(prepare).process(claimed, WORKER);
        assertThat(status(id)).isEqualTo("CANCELLED");
        assertThat(prepare.calls()).isZero();
    }

    @Test
    void aSleepingOrWaitingInvestigationIsCancelledAtOnce() {
        TestActors actor = investigator();
        UUID sleeping = queue(actor, claimFor(actor, "CLM-C2"));
        runner(new ScriptedHandler(Stage.PREPARING_EVIDENCE).then(new StageOutcome.Transient(InvestigationErrorCode.INTERNAL_ERROR, Map.of()))).runNext(WORKER);
        UUID waiting = queue(actor, claimFor(actor, "CLM-C3"));
        runner(new ScriptedHandler(Stage.PREPARING_EVIDENCE).then(new StageOutcome.Waiting(Map.of()))).runNext(WORKER);
        assertThat(status(sleeping)).isEqualTo("RETRY_WAIT");
        assertThat(status(waiting)).isEqualTo("WAITING_FOR_EVIDENCE");

        assertThat(store.requestCancel(actor.organizationId(), sleeping, clock.instant())).isEqualTo(CancelResult.CANCELLED);
        assertThat(store.requestCancel(actor.organizationId(), waiting, clock.instant())).isEqualTo(CancelResult.CANCELLED);

        assertThat(status(sleeping)).isEqualTo("CANCELLED");
        assertThat(status(waiting)).isEqualTo("CANCELLED");
        assertThat(nextAttemptAt(sleeping)).isNull();
    }

    @Test
    void cancellingAWorkingInvestigationAsksTheWorkerToStopAtTheNextSafePoint() {
        TestActors actor = investigator();
        UUID id = queue(actor, claimFor(actor, "CLM-C4"));
        AtomicInteger askedToStop = new AtomicInteger();
        ScriptedHandler extract = new ScriptedHandler(Stage.EXTRACTING).thenDo(context -> {
            // The user cancels while this stage is running.
            assertThat(store.requestCancel(actor.organizationId(), id, clock.instant())).isEqualTo(CancelResult.CANCEL_REQUESTED);
            if (context.shouldStop().getAsBoolean()) {
                askedToStop.incrementAndGet();
            }
            return StageOutcome.ok(Map.of());
        });
        ScriptedHandler rules = new ScriptedHandler(Stage.RUNNING_RULES);

        runner(extract, rules).runNext(WORKER);

        assertThat(askedToStop.get()).isEqualTo(1);
        assertThat(status(id)).isEqualTo("CANCELLED");
        assertThat(rules.calls()).as("nothing runs after the cancel").isZero();
        assertThat(stageStatus(id, Stage.EXTRACTING)).isEqualTo("OK");
        assertThat(leaseOwner(id)).isNull();
    }

    @Test
    void aHandlerThatNoticesTheCancelStopsEarlyAndTheStageIsLeftToRunNeverAgain() {
        TestActors actor = investigator();
        UUID id = queue(actor, claimFor(actor, "CLM-C5"));
        ScriptedHandler extract = new ScriptedHandler(Stage.EXTRACTING).thenDo(context -> {
            store.requestCancel(actor.organizationId(), id, clock.instant());
            return context.shouldStop().getAsBoolean() ? new StageOutcome.Interrupted() : StageOutcome.ok(Map.of());
        });

        runner(extract).runNext(WORKER);

        assertThat(status(id)).isEqualTo("CANCELLED");
        assertThat(stageStatus(id, Stage.EXTRACTING)).as("an unfinished stage is not reported as done").isEqualTo("PENDING");
        assertThat(extract.calls()).isEqualTo(1);
    }

    @Test
    void aFinishedInvestigationCannotBeCancelledAndAnotherOrganizationsLooksMissing() {
        TestActors actor = investigator();
        UUID id = queue(actor, claimFor(actor, "CLM-C6"));
        runner().runNext(WORKER);
        assertThat(status(id)).isEqualTo("COMPLETED");

        assertThat(store.requestCancel(actor.organizationId(), id, clock.instant())).isEqualTo(CancelResult.NOT_ALLOWED);
        assertThat(store.requestCancel(investigator().organizationId(), id, clock.instant())).isEqualTo(CancelResult.NOT_FOUND);
        assertThat(store.requestCancel(actor.organizationId(), UUID.randomUUID(), clock.instant())).isEqualTo(CancelResult.NOT_FOUND);
        assertThat(status(id)).isEqualTo("COMPLETED");
    }

    // ---------------------------------------------------------------- retrying a failed investigation

    @Test
    void retryingAFailedInvestigationResumesAtTheFailedStageAndKeepsTheFinishedOnes() {
        TestActors actor = investigator();
        UUID id = queue(actor, claimFor(actor, "CLM-R1"));
        ScriptedHandler prepare = new ScriptedHandler(Stage.PREPARING_EVIDENCE);
        ScriptedHandler rules = new ScriptedHandler(Stage.RUNNING_RULES).then(new StageOutcome.Permanent(InvestigationErrorCode.INTERNAL_ERROR, Map.of()));
        InvestigationRunner runner = runner(prepare, rules);
        runner.runNext(WORKER);
        assertThat(status(id)).isEqualTo("FAILED");

        assertThat(store.retryFailed(actor.organizationId(), id, 1, clock.instant())).isEqualTo(RetryResult.QUEUED);

        assertThat(status(id)).isEqualTo("QUEUED");
        assertThat(attempt(id)).isEqualTo(1);
        assertThat(errorCode(id)).isNull();
        assertThat(jdbc.queryForObject("select finished_at is null and not cancel_requested from investigations where id = ?", Boolean.class, id)).isTrue();
        assertThat(stageStatus(id, Stage.PREPARING_EVIDENCE)).isEqualTo("OK");
        assertThat(stageStatus(id, Stage.RUNNING_RULES)).isEqualTo("PENDING");

        runner.runNext(WORKER);

        assertThat(status(id)).isEqualTo("COMPLETED");
        assertThat(prepare.calls()).as("the finished stage is not repeated on a retry").isEqualTo(1);
        assertThat(rules.calls()).isEqualTo(2);
    }

    @Test
    void onlyAFailedInvestigationCanBeRetried() {
        TestActors actor = investigator();
        UUID done = queue(actor, claimFor(actor, "CLM-R2"));
        runner().runNext(WORKER);
        UUID queued = queue(actor, claimFor(actor, "CLM-R3"));

        assertThat(store.retryFailed(actor.organizationId(), done, 1, clock.instant())).isEqualTo(RetryResult.NOT_ALLOWED);
        assertThat(store.retryFailed(actor.organizationId(), queued, 1, clock.instant())).isEqualTo(RetryResult.NOT_ALLOWED);
        assertThat(store.retryFailed(investigator().organizationId(), done, 1, clock.instant())).isEqualTo(RetryResult.NOT_FOUND);
    }

    @Test
    void aFailedInvestigationCannotBeRetriedWhileAnotherOneOfTheClaimIsActive() {
        TestActors actor = investigator();
        UUID claim = claimFor(actor, "CLM-R4");
        UUID failed = queue(actor, claim);
        runner(new ScriptedHandler(Stage.PREPARING_EVIDENCE).then(new StageOutcome.Permanent(InvestigationErrorCode.INTERNAL_ERROR, Map.of()))).runNext(WORKER);
        assertThat(status(failed)).isEqualTo("FAILED");
        UUID newer = queue(actor, claim);

        assertThatThrownBy(() -> store.retryFailed(actor.organizationId(), failed, 1, clock.instant())).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(status(failed)).isEqualTo("FAILED");
        assertThat(status(newer)).isEqualTo("QUEUED");
    }

    // ---------------------------------------------------------------- crashes, lost leases and poison jobs

    @Test
    void aWorkerThatDiesMidStageIsReplacedAndTheNewWorkerResumesAtThatStage() {
        TestActors actor = investigator();
        UUID id = queue(actor, claimFor(actor, "CLM-X1"));
        // The first worker claims the job, finishes the first stage and starts the second, then the process dies.
        var claimed = runner().claim("dead-worker").orElseThrow();
        store.beginStage(id, "dead-worker", Stage.PREPARING_EVIDENCE, clock.instant());
        store.finishStage(id, "dead-worker", Stage.PREPARING_EVIDENCE, com.example.ClaimShieldX.investigation.model.StageStatus.OK, null, Map.of(), null, clock.instant());
        store.beginStage(id, "dead-worker", Stage.EXTRACTING, clock.instant());
        assertThat(claimed.attempt()).isEqualTo(1);
        ScriptedHandler prepare = new ScriptedHandler(Stage.PREPARING_EVIDENCE);
        ScriptedHandler extract = new ScriptedHandler(Stage.EXTRACTING);
        InvestigationRunner runner = runner(prepare, extract);

        assertThat(runner.runNext("new-worker")).as("the dead worker's lease is still alive").isFalse();
        clock.advance(props.lease().plusSeconds(1));
        assertThat(runner.runNext("new-worker")).isTrue();

        assertThat(status(id)).isEqualTo("COMPLETED");
        assertThat(prepare.calls()).as("the stage the dead worker finished is not repeated").isZero();
        assertThat(extract.calls()).isEqualTo(1);
        assertThat(extract.contexts().get(0).attempt()).isEqualTo(2);
    }

    @Test
    void aJobThatKeepsKillingItsWorkersIsFailedInsteadOfBeingTriedForEver() {
        TestActors actor = investigator();
        UUID id = queue(actor, claimFor(actor, "CLM-X2"), 2);
        InvestigationRunner runner = runner();
        for (int crash = 0; crash < 2; crash++) {
            runner.claim("doomed-" + crash).orElseThrow();
            store.beginStage(id, "doomed-" + crash, Stage.EXTRACTING, clock.instant());
            clock.advance(props.lease().plusSeconds(1));
        }

        assertThat(runner.runNext("last-chance")).isTrue();

        assertThat(status(id)).isEqualTo("FAILED");
        assertThat(errorCode(id)).isEqualTo("TIMEOUT");
        assertThat(jdbc.queryForObject("select error_message from investigations where id = ?", String.class, id)).isEqualTo(InvestigationErrorCode.TIMEOUT.message());
        assertThat(runner.runNext("another")).isFalse();
    }

    @Test
    void aWorkerThatLosesItsLeaseMidStageStopsWithoutOverwritingTheNewOwner() {
        TestActors actor = investigator();
        UUID id = queue(actor, claimFor(actor, "CLM-X3"));
        ScriptedHandler extract = new ScriptedHandler(Stage.EXTRACTING).thenDo(context -> {
            // Another worker took the job over while this one was stalled.
            jdbc.update("update investigations set lease_owner = 'thief', lease_expires_at = ? where id = ?", java.sql.Timestamp.from(clock.instant().plusSeconds(180)), id);
            return StageOutcome.ok(Map.of("read", 1));
        });
        ScriptedHandler rules = new ScriptedHandler(Stage.RUNNING_RULES);

        runner(extract, rules).runNext(WORKER);

        assertThat(leaseOwner(id)).isEqualTo("thief");
        assertThat(status(id)).isEqualTo("EXTRACTING");
        assertThat(stageStatus(id, Stage.EXTRACTING)).as("the stalled worker's result was not written").isEqualTo("RUNNING");
        assertThat(rules.calls()).isZero();
    }

    @Test
    void aHandlerSeesThatItShouldStopWhenItsLeaseHasBeenTaken() {
        TestActors actor = investigator();
        UUID id = queue(actor, claimFor(actor, "CLM-X4"));
        AtomicInteger stopSeen = new AtomicInteger();
        ScriptedHandler extract = new ScriptedHandler(Stage.EXTRACTING).thenDo(context -> {
            jdbc.update("update investigations set lease_owner = 'thief' where id = ?", id);
            if (context.shouldStop().getAsBoolean()) {
                stopSeen.incrementAndGet();
                return new StageOutcome.Interrupted();
            }
            return StageOutcome.ok(Map.of());
        });

        runner(extract).runNext(WORKER);

        assertThat(stopSeen.get()).isEqualTo(1);
        assertThat(leaseOwner(id)).isEqualTo("thief");
        assertThat(status(id)).isEqualTo("EXTRACTING");
    }

    // ---------------------------------------------------------------- concurrency

    @Test
    @Timeout(120)
    void manyWorkersRunningAtOnceNeverRunTheSameStageTwice() throws Exception {
        UUID id = newInvestigation();
        ScriptedHandler prepare = new ScriptedHandler(Stage.PREPARING_EVIDENCE).thenDo(c -> slow());
        ScriptedHandler extract = new ScriptedHandler(Stage.EXTRACTING).thenDo(c -> slow());
        ScriptedHandler rules = new ScriptedHandler(Stage.RUNNING_RULES).thenDo(c -> slow());
        InvestigationRunner runner = runner(prepare, extract, rules);
        int workers = 8;
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int w = 0; w < workers; w++) {
                String owner = "w" + w;
                futures.add(pool.submit(() -> {
                    go.await();
                    return runner.runNext(owner);
                }));
            }
            go.countDown();
            int worked = 0;
            for (Future<Boolean> future : futures) {
                if (future.get(90, TimeUnit.SECONDS)) {
                    worked++;
                }
            }
            assertThat(worked).as("exactly one worker got the job").isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }

        assertThat(status(id)).isEqualTo("COMPLETED");
        assertThat(prepare.calls()).isEqualTo(1);
        assertThat(extract.calls()).isEqualTo(1);
        assertThat(rules.calls()).isEqualTo(1);
    }

    private static StageOutcome slow() {
        try {
            Thread.sleep(150);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return StageOutcome.ok(Map.of());
    }
}
