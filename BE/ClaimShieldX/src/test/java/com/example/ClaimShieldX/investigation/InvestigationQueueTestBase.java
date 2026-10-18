package com.example.ClaimShieldX.investigation;

import com.example.ClaimShieldX.investigation.config.InvestigationProperties;
import com.example.ClaimShieldX.investigation.model.Stage;
import com.example.ClaimShieldX.investigation.work.Backoff;
import com.example.ClaimShieldX.investigation.work.InvestigationRunner;
import com.example.ClaimShieldX.investigation.work.InvestigationStore;
import com.example.ClaimShieldX.investigation.work.StageHandler;
import com.example.ClaimShieldX.investigation.work.StageHandlers;
import com.example.ClaimShieldX.rules.RuleApiTestBase;
import com.example.ClaimShieldX.support.TestActors;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.random.RandomGenerator;

/** Real database; the queue is driven by hand with a clock the test moves and stage handlers the test scripts. */
public abstract class InvestigationQueueTestBase extends RuleApiTestBase {
    @Autowired protected InvestigationStore store;

    protected final MutableClock clock = new MutableClock(Instant.parse("2026-10-18T10:00:00Z"));
    protected InvestigationProperties props = InvestigationProperties.defaults();

    /** Always answers 0.5, which is "no jitter": the delays are exactly 30 s, 60 s, 120 s. */
    protected static final RandomGenerator NO_JITTER = new RandomGenerator() {
        @Override
        public long nextLong() {
            return 0;
        }

        @Override
        public double nextDouble() {
            return 0.5;
        }
    };

    /**
     * The queue is global: a worker takes the oldest due investigation of ANY organization. A test run that was killed part-way leaves its
     * investigations behind, and they would be picked up by the next run's workers. This is the dedicated test database, so each test
     * starts from an empty queue.
     */
    @org.junit.jupiter.api.BeforeEach
    void startFromAnEmptyQueue() {
        jdbc.update("delete from investigation_stages");
        jdbc.update("delete from investigations");
    }

    protected UUID queue(TestActors actor, UUID claim) {
        return queue(actor, claim, props.maxAttempts());
    }

    protected UUID queue(TestActors actor, UUID claim, int maxAttempts) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(clock.instant());
        jdbc.update("""
                insert into investigations(id, created_at, updated_at, organization_id, claim_id, claim_version, requested_by, status, attempt, max_attempts)
                values (?, ?, ?, ?, ?, 1, ?, 'QUEUED', 1, ?)
                """, id, now, now, actor.organizationId(), claim, actor.userId(), maxAttempts);
        return id;
    }

    protected InvestigationRunner runner(StageHandler... handlers) {
        return new InvestigationRunner(store, new StageHandlers(List.of(handlers)), props, clock, new Backoff(props, NO_JITTER));
    }

    protected String status(UUID id) {
        return jdbc.queryForObject("select status from investigations where id = ?", String.class, id);
    }

    protected int attempt(UUID id) {
        return jdbc.queryForObject("select attempt from investigations where id = ?", Integer.class, id);
    }

    protected Instant nextAttemptAt(UUID id) {
        Timestamp ts = jdbc.queryForObject("select next_attempt_at from investigations where id = ?", Timestamp.class, id);
        return ts == null ? null : ts.toInstant();
    }

    protected String leaseOwner(UUID id) {
        return jdbc.queryForObject("select lease_owner from investigations where id = ?", String.class, id);
    }

    protected String errorCode(UUID id) {
        return jdbc.queryForObject("select error_code from investigations where id = ?", String.class, id);
    }

    protected String stageStatus(UUID id, Stage stage) {
        List<String> found = jdbc.queryForList("select status from investigation_stages where investigation_id = ? and stage = ?", String.class, id, stage.name());
        return found.isEmpty() ? null : found.get(0);
    }

    protected UUID claimFor(TestActors actor, String name) {
        return ruleClaim(actor, name);
    }
}
