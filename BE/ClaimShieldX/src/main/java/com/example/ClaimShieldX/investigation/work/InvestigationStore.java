package com.example.ClaimShieldX.investigation.work;

import com.example.ClaimShieldX.investigation.model.InvestigationErrorCode;
import com.example.ClaimShieldX.investigation.model.InvestigationStatus;
import com.example.ClaimShieldX.investigation.model.Stage;
import com.example.ClaimShieldX.investigation.model.StageStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Every change to the queue state of an investigation. Each method is one short transaction, and every change a worker makes is
 * guarded by "I still hold the lease", so a worker whose lease was taken over (it stalled, or the process was paused) can never
 * overwrite what the new owner did: its updates simply match no row. Time is always passed in, never read here.
 */
@Service
public class InvestigationStore {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String CLAIMABLE = "'QUEUED','PREPARING_EVIDENCE','EXTRACTING','RUNNING_RULES','BUILDING_HYPOTHESES','VALIDATING_FINDINGS','GENERATING_REPORT','RETRY_WAIT','WAITING_FOR_EVIDENCE'";
    private static final String RUNNING = "'PREPARING_EVIDENCE','EXTRACTING','RUNNING_RULES','BUILDING_HYPOTHESES','VALIDATING_FINDINGS','GENERATING_REPORT'";

    public record Claimed(UUID id, UUID organizationId, UUID claimId, UUID requestedBy, InvestigationStatus status, int attempt, int maxAttempts, boolean cancelRequested) {
    }

    public enum Began {STARTED, CANCEL_REQUESTED, LEASE_LOST}

    public enum CancelResult {CANCELLED, CANCEL_REQUESTED, NOT_ALLOWED, NOT_FOUND}

    public enum RetryResult {QUEUED, NOT_ALLOWED, NOT_FOUND}

    private final JdbcTemplate jdbc;

    public InvestigationStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static Timestamp ts(Instant instant) {
        return instant == null ? null : Timestamp.from(instant.truncatedTo(ChronoUnit.MICROS));
    }

    private static String json(Map<String, Object> value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize a stage summary", e);
        }
    }

    // ------------------------------------------------------------------------------------------------ claiming

    /**
     * Takes the oldest due investigation in one statement: the candidate row is locked with SKIP LOCKED (other workers pass over it)
     * and given a lease in the same statement. An investigation left in a running stage by a worker whose lease ran out is taken
     * again and counts as a new attempt, which bounds a job that keeps killing its worker.
     */
    @Transactional
    public Optional<Claimed> claimNext(String owner, Instant now, Instant leaseUntil) {
        List<Claimed> rows = jdbc.query("""
                with candidate as (
                    select id from investigations
                    where status in (%s)
                      and (lease_expires_at is null or lease_expires_at < ?)
                      and (next_attempt_at is null or next_attempt_at <= ?)
                    order by coalesce(next_attempt_at, created_at), id
                    limit 1
                    for update skip locked)
                update investigations i
                   set lease_owner = ?, lease_expires_at = ?, updated_at = ?,
                       attempt = case when i.status in (%s) then i.attempt + 1 else i.attempt end,
                       started_at = coalesce(i.started_at, ?)
                  from candidate c
                 where i.id = c.id
                returning i.id, i.organization_id, i.claim_id, i.requested_by, i.status, i.attempt, i.max_attempts, i.cancel_requested
                """.formatted(CLAIMABLE, RUNNING),
                (rs, n) -> new Claimed(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getObject(3, UUID.class), rs.getObject(4, UUID.class),
                        InvestigationStatus.valueOf(rs.getString(5)), rs.getInt(6), rs.getInt(7), rs.getBoolean(8)),
                ts(now), ts(now), owner, ts(leaseUntil), ts(now), ts(now));
        return rows.stream().findFirst();
    }

    /** Extends the lease of everything this worker holds. Returns how many it still held. */
    @Transactional
    public int heartbeat(String owner, Instant leaseUntil) {
        return jdbc.update("update investigations set lease_expires_at = ? where lease_owner = ? and status in (" + CLAIMABLE + ")", ts(leaseUntil), owner);
    }

    @Transactional(readOnly = true)
    public boolean holdsLease(UUID id, String owner) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from investigations where id = ? and lease_owner = ?)", Boolean.class, id, owner));
    }

    @Transactional(readOnly = true)
    public boolean cancelRequested(UUID id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select coalesce((select cancel_requested from investigations where id = ?), false)", Boolean.class, id));
    }

    @Transactional(readOnly = true)
    public Map<Stage, StageStatus> stageStatuses(UUID id) {
        Map<Stage, StageStatus> map = new EnumMap<>(Stage.class);
        jdbc.query("select stage, status from investigation_stages where investigation_id = ?",
                rs -> {
                    map.put(Stage.valueOf(rs.getString(1)), StageStatus.valueOf(rs.getString(2)));
                }, id);
        return map;
    }

    /** The summary a stage stored when it last finished or slept; empty if the stage has not run. */
    @Transactional(readOnly = true)
    public Map<String, Object> stageSummary(UUID id, Stage stage) {
        return jdbc.queryForList("select summary::text from investigation_stages where investigation_id = ? and stage = ?", String.class, id, stage.name()).stream()
                .findFirst().map(com.example.ClaimShieldX.analysis.Json::map).orElse(Map.of());
    }

    // ------------------------------------------------------------------------------------------------ worker transitions

    @Transactional
    public Began beginStage(UUID id, String owner, Stage stage, Instant now) {
        int updated = jdbc.update("""
                update investigations set status = ?, next_attempt_at = null, updated_at = ?
                 where id = ? and lease_owner = ? and cancel_requested = false
                """, stage.name(), ts(now), id, owner);
        if (updated == 0) {
            return holdsLease(id, owner) ? Began.CANCEL_REQUESTED : Began.LEASE_LOST;
        }
        jdbc.update("""
                insert into investigation_stages(id, investigation_id, organization_id, claim_id, stage, status, attempts, started_at, updated_at, summary)
                select ?, i.id, i.organization_id, i.claim_id, ?, 'RUNNING', 1, ?, ?, '{}'::jsonb from investigations i where i.id = ?
                on conflict (investigation_id, stage) do update
                   set status = 'RUNNING', attempts = investigation_stages.attempts + 1, started_at = excluded.started_at,
                       finished_at = null, updated_at = excluded.updated_at, error_code = null
                """, UUID.randomUUID(), stage.name(), ts(now), ts(now), id);
        return Began.STARTED;
    }

    /** Records a finished stage. False when the lease was lost, in which case nothing was written. */
    @Transactional
    public boolean finishStage(UUID id, String owner, Stage stage, StageStatus status, InvestigationErrorCode error, Map<String, Object> summary, UUID ruleRunId, Instant now) {
        if (jdbc.update("update investigations set updated_at = ?, rule_run_id = coalesce(?, rule_run_id) where id = ? and lease_owner = ?", ts(now), ruleRunId, id, owner) == 0) {
            return false;
        }
        jdbc.update("update investigation_stages set status = ?, finished_at = ?, updated_at = ?, error_code = ?, summary = ?::jsonb where investigation_id = ? and stage = ?",
                status.name(), ts(now), ts(now), error == null ? null : error.name(), json(summary), id, stage.name());
        return true;
    }

    /** Puts the investigation to sleep until {@code nextAttemptAt}; the stage will run again then. The next attempt is attempt + 1. */
    @Transactional
    public boolean scheduleRetry(UUID id, String owner, Stage stage, InvestigationErrorCode error, Map<String, Object> summary, Instant nextAttemptAt, Instant now) {
        int updated = jdbc.update("""
                update investigations set status = 'RETRY_WAIT', attempt = attempt + 1, next_attempt_at = ?, lease_owner = null, lease_expires_at = null,
                       error_code = ?, error_message = ?, updated_at = ?
                 where id = ? and lease_owner = ?
                """, ts(nextAttemptAt), error.name(), error.message(), ts(now), id, owner);
        if (updated == 0) {
            return false;
        }
        jdbc.update("update investigation_stages set status = 'PENDING', updated_at = ?, error_code = ?, summary = ?::jsonb where investigation_id = ? and stage = ?",
                ts(now), error.name(), json(summary), id, stage.name());
        return true;
    }

    /** Sleeps until {@code retryAt} WITHOUT using an attempt (the stage was only waiting for something to be released, nothing failed). */
    @Transactional
    public boolean sleepUntil(UUID id, String owner, Stage stage, Map<String, Object> summary, Instant retryAt, Instant now) {
        int updated = jdbc.update("""
                update investigations set status = 'RETRY_WAIT', next_attempt_at = ?, lease_owner = null, lease_expires_at = null,
                       error_code = null, error_message = null, updated_at = ?
                 where id = ? and lease_owner = ?
                """, ts(retryAt), ts(now), id, owner);
        if (updated == 0) {
            return false;
        }
        jdbc.update("update investigation_stages set status = 'PENDING', updated_at = ?, error_code = null, summary = ?::jsonb where investigation_id = ? and stage = ?",
                ts(now), json(summary), id, stage.name());
        return true;
    }

    @Transactional
    public boolean waitForEvidence(UUID id, String owner, Map<String, Object> summary, Instant recheckAt, Instant now) {
        int updated = jdbc.update("""
                update investigations set status = 'WAITING_FOR_EVIDENCE', next_attempt_at = ?, lease_owner = null, lease_expires_at = null,
                       error_code = null, error_message = null, updated_at = ?
                 where id = ? and lease_owner = ?
                """, ts(recheckAt), ts(now), id, owner);
        if (updated == 0) {
            return false;
        }
        jdbc.update("update investigation_stages set status = 'PENDING', updated_at = ?, summary = ?::jsonb where investigation_id = ? and stage = 'PREPARING_EVIDENCE'",
                ts(now), json(summary), id);
        return true;
    }

    @Transactional
    public boolean complete(UUID id, String owner, Instant now) {
        boolean done = jdbc.update("""
                update investigations set status = 'COMPLETED', finished_at = ?, updated_at = ?, next_attempt_at = null, lease_owner = null, lease_expires_at = null,
                       error_code = null, error_message = null
                 where id = ? and lease_owner = ?
                """, ts(now), ts(now), id, owner) == 1;
        if (done) {
            audit(id, "INVESTIGATION_COMPLETED", "{}");
        }
        return done;
    }

    /** Written in the same transaction as the change it records, so the trail and the state cannot disagree. */
    private void audit(UUID investigationId, String type, String detailJson) {
        jdbc.update("insert into audit_events(id, organization_id, claim_id, investigation_id, actor_user_id, event_type, detail) select ?, organization_id, claim_id, id, null, ?, ?::jsonb from investigations where id = ?",
                UUID.randomUUID(), type, detailJson, investigationId);
    }

    /** Ends the investigation as FAILED. {@code stage} is the stage that failed, if the failure belongs to one. */
    @Transactional
    public boolean fail(UUID id, String owner, Stage stage, InvestigationErrorCode error, Map<String, Object> summary, Instant now) {
        int updated = jdbc.update("""
                update investigations set status = 'FAILED', finished_at = ?, updated_at = ?, next_attempt_at = null, lease_owner = null, lease_expires_at = null,
                       error_code = ?, error_message = ?
                 where id = ? and lease_owner = ?
                """, ts(now), ts(now), error.name(), error.message(), id, owner);
        if (updated == 0) {
            return false;
        }
        if (stage != null) {
            jdbc.update("update investigation_stages set status = 'FAILED', finished_at = ?, updated_at = ?, error_code = ?, summary = ?::jsonb where investigation_id = ? and stage = ?",
                    ts(now), ts(now), error.name(), json(summary), id, stage.name());
        }
        audit(id, "INVESTIGATION_FAILED", json(Map.of("errorCode", error.name(), "stage", stage == null ? "NONE" : stage.name())));
        return true;
    }

    @Transactional
    public boolean cancel(UUID id, String owner, Instant now) {
        int updated = jdbc.update("""
                update investigations set status = 'CANCELLED', finished_at = ?, updated_at = ?, next_attempt_at = null, lease_owner = null, lease_expires_at = null
                 where id = ? and lease_owner = ?
                """, ts(now), ts(now), id, owner);
        if (updated == 1) {
            jdbc.update("update investigation_stages set status = 'PENDING', updated_at = ? where investigation_id = ? and status = 'RUNNING'", ts(now), id);
            audit(id, "INVESTIGATION_CANCELLED", "{}");
        }
        return updated == 1;
    }

    // ------------------------------------------------------------------------------------------------ requests from the API

    /**
     * Cancels at once when nothing is working on it (queued, waiting, sleeping before a retry); otherwise asks the worker to stop at
     * its next safe point. Both are conditional updates, so a worker claiming the row at the same instant simply wins or loses cleanly.
     */
    @Transactional
    public CancelResult requestCancel(UUID organizationId, UUID id, Instant now) {
        int cancelled = jdbc.update("""
                update investigations set status = 'CANCELLED', finished_at = ?, updated_at = ?, next_attempt_at = null, lease_owner = null, lease_expires_at = null
                 where id = ? and organization_id = ? and status in ('QUEUED','WAITING_FOR_EVIDENCE','RETRY_WAIT') and (lease_owner is null or lease_expires_at < ?)
                """, ts(now), ts(now), id, organizationId, ts(now));
        if (cancelled == 1) {
            audit(id, "INVESTIGATION_CANCELLED", "{}");
            return CancelResult.CANCELLED;
        }
        int requested = jdbc.update("""
                update investigations set cancel_requested = true, updated_at = ?
                 where id = ? and organization_id = ? and status not in ('COMPLETED','FAILED','CANCELLED')
                """, ts(now), id, organizationId);
        if (requested == 1) {
            return CancelResult.CANCEL_REQUESTED;
        }
        boolean exists = Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from investigations where id = ? and organization_id = ?)", Boolean.class, id, organizationId));
        return exists ? CancelResult.NOT_ALLOWED : CancelResult.NOT_FOUND;
    }

    /**
     * Puts a FAILED investigation back in the queue as a new attempt. Stages that finished stay finished; the failed one runs again.
     * The unique index refuses this while another investigation of the claim is active, which surfaces as a DataIntegrityViolationException.
     */
    @Transactional
    public RetryResult retryFailed(UUID organizationId, UUID id, int firstAttempt, Instant now) throws DataIntegrityViolationException {
        int updated = jdbc.update("""
                update investigations set status = 'QUEUED', attempt = ?, next_attempt_at = null, error_code = null, error_message = null, finished_at = null,
                       cancel_requested = false, lease_owner = null, lease_expires_at = null, updated_at = ?
                 where id = ? and organization_id = ? and status = 'FAILED'
                """, firstAttempt, ts(now), id, organizationId);
        if (updated == 0) {
            boolean exists = Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from investigations where id = ? and organization_id = ?)", Boolean.class, id, organizationId));
            return exists ? RetryResult.NOT_ALLOWED : RetryResult.NOT_FOUND;
        }
        jdbc.update("update investigation_stages set status = 'PENDING', error_code = null, updated_at = ? where investigation_id = ? and status = 'FAILED'", ts(now), id);
        return RetryResult.QUEUED;
    }

    /** A document arrived for the claim: any investigation waiting for evidence checks again at once instead of at its next scheduled re-check. */
    @Transactional
    public int wakeWaiting(UUID organizationId, UUID claimId, Instant now) {
        return jdbc.update("update investigations set next_attempt_at = ?, updated_at = ? where organization_id = ? and claim_id = ? and status = 'WAITING_FOR_EVIDENCE'",
                ts(now), ts(now), organizationId, claimId);
    }
}
