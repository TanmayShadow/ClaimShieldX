package com.example.ClaimShieldX.analysis.review;

import com.example.ClaimShieldX.analysis.dto.AnalysisDtos.ActionRequest;
import com.example.ClaimShieldX.analysis.dto.AnalysisDtos.ReviewActionView;
import com.example.ClaimShieldX.analysis.report.ReportService;
import com.example.ClaimShieldX.analysis.snapshot.SnapshotService;
import com.example.ClaimShieldX.auth.model.UserPrincipal;
import com.example.ClaimShieldX.common.utils.TokenHasher;
import com.example.ClaimShieldX.exceptions.InvalidStateTransitionException;
import com.example.ClaimShieldX.exceptions.ReportVersionMismatchException;
import com.example.ClaimShieldX.exceptions.ResourceNotFoundException;
import com.example.ClaimShieldX.idempotency.service.IdempotencyService;
import com.example.ClaimShieldX.investigation.model.Investigation;
import com.example.ClaimShieldX.investigation.model.InvestigationStatus;
import com.example.ClaimShieldX.investigation.repository.InvestigationRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Records what a person decided after reading a report. Append-only: nothing updates or deletes a decision. Each record keeps what the tool
 * had said at that moment (score, recommended action, whether newer evidence had arrived), and the decision and its audit event are written
 * in one transaction. The tool never calls this: only a signed-in person can.
 */
@Service
public class ReviewService {
    static final String OPERATION = "investigation.action";

    public record Result(ReviewActionView action, boolean replayed) {
    }

    private final JdbcTemplate jdbc;
    private final InvestigationRepository investigations;
    private final ReportService reports;
    private final SnapshotService snapshots;
    private final IdempotencyService idempotency;
    private final ReviewRecords records;

    public ReviewService(JdbcTemplate jdbc, InvestigationRepository investigations, ReportService reports, SnapshotService snapshots, IdempotencyService idempotency, ReviewRecords records) {
        this.jdbc = jdbc;
        this.investigations = investigations;
        this.reports = reports;
        this.snapshots = snapshots;
        this.idempotency = idempotency;
        this.records = records;
    }

    public Result record(UserPrincipal principal, UUID investigationId, ActionRequest request, String idempotencyKey) {
        IdempotencyService.validateKey(idempotencyKey);
        UUID org = principal.getOrganization().getId();
        Investigation investigation = investigations.findByIdAndOrganizationId(investigationId, org).orElseThrow(() -> new ResourceNotFoundException("Investigation not found"));
        var report = reports.findByInvestigation(org, investigationId).orElse(null);
        if (investigation.getStatus() != InvestigationStatus.COMPLETED || report == null) {
            throw new InvalidStateTransitionException("The investigation has no report to decide on yet");
        }
        if (request.reportVersion() != report.reportVersion()) {
            throw new ReportVersionMismatchException();
        }
        String rationale = request.rationale().strip();
        if (rationale.length() < 10) {
            throw com.example.ClaimShieldX.exceptions.RequestValidationException.of("rationale", "must be 10 to 2000 characters");
        }
        IdempotencyService.Begin begin = idempotency.begin(org, OPERATION, idempotencyKey,
                IdempotencyService.fingerprint(investigationId.toString(), request.action().name(), String.valueOf(request.reportVersion()), TokenHasher.sha256Hex(rationale)));
        if (begin.mode() == IdempotencyService.Mode.REPLAY) {
            return new Result(find(org, begin.resourceId()), true);
        }
        try {
            var snapshot = snapshots.find(org, investigationId).orElseThrow();
            boolean newerReport = reports.latestForClaim(org, investigation.getClaimId()).map(r -> r.reportVersion() > report.reportVersion()).orElse(false);
            boolean stale = newerReport || reports.isStale(org, report, snapshot.createdAt());
            UUID id = records.insert(org, investigation, report, principal.getId(), request.action().name(), rationale, stale);
            idempotency.complete(begin.keyId(), 201, "REVIEW_ACTION", id);
            return new Result(find(org, id), false);
        } catch (RuntimeException e) {
            idempotency.release(begin.keyId());
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public List<ReviewActionView> list(UUID organizationId, UUID investigationId) {
        return jdbc.query(select() + "where organization_id = ? and investigation_id = ? order by created_at, id", (rs, n) -> map(rs), organizationId, investigationId);
    }

    private ReviewActionView find(UUID organizationId, UUID id) {
        return jdbc.query(select() + "where organization_id = ? and id = ?", (rs, n) -> map(rs), organizationId, id).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("A completed idempotency key points at a missing decision"));
    }

    private static String select() {
        return "select id, created_at, action, rationale, created_by, report_version, report_was_stale, recommended_action, score from review_actions ";
    }

    private static ReviewActionView map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ReviewActionView(rs.getObject(1, UUID.class), rs.getTimestamp(2).toInstant(), rs.getString(3), rs.getString(4), rs.getObject(5, UUID.class), rs.getInt(6), rs.getBoolean(7), rs.getString(8), rs.getInt(9));
    }

    @Service
    static class ReviewRecords {
        private final JdbcTemplate jdbc;
        private final AuditService audit;

        ReviewRecords(JdbcTemplate jdbc, AuditService audit) {
            this.jdbc = jdbc;
            this.audit = audit;
        }

        @Transactional
        UUID insert(UUID org, Investigation investigation, ReportService.Report report, UUID actor, String action, String rationale, boolean stale) {
            UUID id = UUID.randomUUID();
            jdbc.update("""
                    insert into review_actions(id, organization_id, claim_id, investigation_id, report_id, snapshot_id, created_by, action, rationale, report_version, report_was_stale, recommended_action, score)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, id, org, investigation.getClaimId(), investigation.getId(), report.id(), report.snapshotId(), actor, action, rationale, report.reportVersion(), stale, report.recommendedAction(), report.score());
            audit.recordStrict(org, investigation.getClaimId(), investigation.getId(), actor, "REVIEW_ACTION_RECORDED",
                    Map.of("action", action, "reportVersion", report.reportVersion(), "reportWasStale", stale, "actionId", id.toString()));
            return id;
        }
    }
}
