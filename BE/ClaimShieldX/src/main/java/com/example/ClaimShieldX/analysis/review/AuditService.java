package com.example.ClaimShieldX.analysis.review;

import com.example.ClaimShieldX.analysis.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The audit trail: who or what did what to an investigation, when. Append-only. A failure to write an audit event never fails the
 * action it records on the system side (it is logged), but the review action of a person writes its own record in the same
 * transaction, so the decision itself is never lost.
 */
@Service
public class AuditService {
    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    public record Event(UUID id, Instant createdAt, UUID investigationId, UUID actorUserId, String eventType, Map<String, Object> detail) {
    }

    private final JdbcTemplate jdbc;

    public AuditService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Never throws: an audit write problem must not undo or hide the thing that happened. */
    public void record(UUID organizationId, UUID claimId, UUID investigationId, UUID actorUserId, String eventType, Map<String, Object> detail) {
        try {
            jdbc.update("insert into audit_events(id, organization_id, claim_id, investigation_id, actor_user_id, event_type, detail) values (?, ?, ?, ?, ?, ?, ?::jsonb)",
                    UUID.randomUUID(), organizationId, claimId, investigationId, actorUserId, eventType, Json.write(detail));
        } catch (RuntimeException e) {
            log.error("Could not write audit event {} for investigation {}: {}", eventType, investigationId, e.getClass().getSimpleName());
        }
    }

    /** For a person's action: written inside the caller's transaction and allowed to fail it, because the decision and its record go together. */
    public void recordStrict(UUID organizationId, UUID claimId, UUID investigationId, UUID actorUserId, String eventType, Map<String, Object> detail) {
        jdbc.update("insert into audit_events(id, organization_id, claim_id, investigation_id, actor_user_id, event_type, detail) values (?, ?, ?, ?, ?, ?, ?::jsonb)",
                UUID.randomUUID(), organizationId, claimId, investigationId, actorUserId, eventType, Json.write(detail));
    }

    public List<Event> forInvestigation(UUID organizationId, UUID investigationId, int limit, int offset) {
        return jdbc.query("select id, created_at, investigation_id, actor_user_id, event_type, detail::text from audit_events where organization_id = ? and investigation_id = ? order by created_at, id limit ? offset ?",
                (rs, n) -> new Event(rs.getObject(1, UUID.class), ((Timestamp) rs.getTimestamp(2)).toInstant(), rs.getObject(3, UUID.class), rs.getObject(4, UUID.class), rs.getString(5), Json.map(rs.getString(6))),
                organizationId, investigationId, limit, offset);
    }

    public long count(UUID organizationId, UUID investigationId) {
        Long count = jdbc.queryForObject("select count(*) from audit_events where organization_id = ? and investigation_id = ?", Long.class, organizationId, investigationId);
        return count == null ? 0 : count;
    }
}
