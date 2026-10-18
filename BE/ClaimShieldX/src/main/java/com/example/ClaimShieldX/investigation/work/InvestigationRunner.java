package com.example.ClaimShieldX.investigation.work;

import com.example.ClaimShieldX.investigation.config.InvestigationProperties;
import com.example.ClaimShieldX.investigation.model.InvestigationErrorCode;
import com.example.ClaimShieldX.investigation.model.Stage;
import com.example.ClaimShieldX.investigation.model.StageStatus;
import com.example.ClaimShieldX.investigation.work.InvestigationStore.Began;
import com.example.ClaimShieldX.investigation.work.InvestigationStore.Claimed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Walks one claimed investigation through its stages. No database transaction is open while a stage works: each step is a
 * separate short, lease-guarded write, so a slow model call never holds a connection, and a crash at any point leaves a state
 * the next worker can resume from (the first stage that is not settled).
 *
 * <p>Transient failures sleep and retry with backoff while attempts remain. When they run out, a stage that can degrade carries
 * on without its result (the rules still run on whatever was read), and any other stage fails the investigation. Nothing here
 * ever stores an exception message: only a code from a fixed list.
 */
@Service
public class InvestigationRunner {
    private static final Logger log = LoggerFactory.getLogger(InvestigationRunner.class);
    private static final int MAX_INTERRUPTIONS = 3;

    private final InvestigationStore store;
    private final StageHandlers handlers;
    private final InvestigationProperties properties;
    private final Clock clock;
    private final Backoff backoff;

    public InvestigationRunner(InvestigationStore store, StageHandlers handlers, InvestigationProperties properties, Clock investigationClock, Backoff backoff) {
        this.store = store;
        this.handlers = handlers;
        this.properties = properties;
        this.clock = investigationClock;
        this.backoff = backoff;
    }

    public Optional<Claimed> claim(String owner) {
        Instant now = clock.instant();
        return store.claimNext(owner, now, now.plus(properties.lease()));
    }

    /** Claims one due investigation and works on it until it finishes, sleeps or is lost. False when nothing was due. */
    public boolean runNext(String owner) {
        Optional<Claimed> claimed = claim(owner);
        claimed.ifPresent(c -> process(c, owner));
        return claimed.isPresent();
    }

    public void process(Claimed claimed, String owner) {
        UUID id = claimed.id();
        int attempt = claimed.attempt();
        if (attempt > claimed.maxAttempts()) {
            log.error("Investigation {} was abandoned by its workers too many times and is failed", id);
            store.fail(id, owner, null, InvestigationErrorCode.TIMEOUT, Map.of("reason", "ATTEMPTS_USED_UP_BY_INTERRUPTED_WORKERS"), clock.instant());
            return;
        }
        int interruptions = 0;
        while (true) {
            Instant now = clock.instant();
            if (!store.holdsLease(id, owner)) {
                return;
            }
            if (store.cancelRequested(id)) {
                store.cancel(id, owner, now);
                return;
            }
            Stage stage = firstUnsettled(store.stageStatuses(id));
            if (stage == null) {
                store.complete(id, owner, now);
                log.info("Investigation {} completed", id);
                return;
            }
            Began began = store.beginStage(id, owner, stage, now);
            if (began == Began.LEASE_LOST) {
                return;
            }
            if (began == Began.CANCEL_REQUESTED) {
                store.cancel(id, owner, now);
                return;
            }

            StageHandler handler = handlers.of(stage);
            StageContext context = new StageContext(id, claimed.organizationId(), claimed.claimId(), claimed.requestedBy(), attempt, claimed.maxAttempts(),
                    () -> !store.holdsLease(id, owner) || store.cancelRequested(id));
            StageOutcome outcome = execute(handler, context);
            Instant after = clock.instant();

            if (outcome instanceof StageOutcome.Done done) {
                if (!store.finishStage(id, owner, stage, done.status(), null, done.summary(), done.ruleRunId(), after)) {
                    return;
                }
            } else if (outcome instanceof StageOutcome.Busy busy) {
                log.info("Investigation {} stage {} is waiting for a document held elsewhere until {}", id, stage, busy.retryAt());
                store.sleepUntil(id, owner, stage, busy.summary(), busy.retryAt(), after);
                return;
            } else if (outcome instanceof StageOutcome.Waiting waiting) {
                store.waitForEvidence(id, owner, waiting.summary(), after.plus(properties.waitingRecheck()), after);
                return;
            } else if (outcome instanceof StageOutcome.Permanent permanent) {
                log.warn("Investigation {} failed in stage {} with {}", id, stage, permanent.code());
                store.fail(id, owner, stage, permanent.code(), permanent.summary(), after);
                return;
            } else if (outcome instanceof StageOutcome.Transient transientFailure) {
                if (attempt < claimed.maxAttempts()) {
                    Instant retryAt = after.plus(backoff.delayAfter(attempt));
                    log.warn("Investigation {} stage {} hit {}; attempt {} of {} will retry at {}", id, stage, transientFailure.code(), attempt, claimed.maxAttempts(), retryAt);
                    store.scheduleRetry(id, owner, stage, transientFailure.code(), transientFailure.summary(), retryAt, after);
                    return;
                }
                if (handler.degradesWhenRetriesRunOut()) {
                    Map<String, Object> summary = new HashMap<>(transientFailure.summary());
                    summary.put("retriesExhausted", true);
                    summary.put("errorCode", transientFailure.code().name());
                    log.warn("Investigation {} stage {} is degraded after {} attempts ({})", id, stage, attempt, transientFailure.code());
                    if (!store.finishStage(id, owner, stage, StageStatus.DEGRADED, transientFailure.code(), summary, null, after)) {
                        return;
                    }
                } else {
                    log.warn("Investigation {} failed in stage {} after {} attempts ({})", id, stage, attempt, transientFailure.code());
                    store.fail(id, owner, stage, transientFailure.code(), transientFailure.summary(), after);
                    return;
                }
            } else {
                // Interrupted: the loop's own checks end the work if it was a cancel or a lost lease. A handler that stops for no
                // such reason would otherwise be asked again for ever.
                if (++interruptions > MAX_INTERRUPTIONS) {
                    store.fail(id, owner, stage, InvestigationErrorCode.INTERNAL_ERROR, Map.of("reason", "STAGE_KEPT_STOPPING"), after);
                    return;
                }
            }
        }
    }

    private StageOutcome execute(StageHandler handler, StageContext context) {
        try {
            return handler.execute(context);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new StageOutcome.Interrupted();
        } catch (Exception e) {
            // The class only: a message can carry document text, identifiers or amounts.
            log.error("Stage {} of investigation {} failed unexpectedly with {}", handler.stage(), context.investigationId(), e.getClass().getSimpleName());
            return new StageOutcome.Transient(InvestigationErrorCode.INTERNAL_ERROR, Map.of());
        }
    }

    private static Stage firstUnsettled(Map<Stage, StageStatus> statuses) {
        for (Stage stage : Stage.values()) {
            StageStatus status = statuses.get(stage);
            if (status == null || !status.isSettled()) {
                return stage;
            }
        }
        return null;
    }
}
