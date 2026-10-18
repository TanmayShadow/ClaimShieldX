package com.example.ClaimShieldX.analysis.report;

import com.example.ClaimShieldX.analysis.Json;
import com.example.ClaimShieldX.analysis.findings.ScoreCalculator;
import com.example.ClaimShieldX.analysis.review.AuditService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Stores and reads the report of an investigation. A report is written once; a newer investigation is a newer version of the claim's report. */
@Service
public class ReportService {
    public record Report(UUID id, Instant createdAt, UUID investigationId, UUID claimId, UUID snapshotId, int reportVersion, String completeness,
                         List<String> unavailableSections, String summary, int score, String band, String recommendedAction, List<Object> components,
                         String scoreVersion, String ruleSetVersion) {
    }

    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public ReportService(JdbcTemplate jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    /** Idempotent: the investigation's report if it already has one. */
    @Transactional
    public UUID generate(UUID organizationId, UUID claimId, UUID investigationId, UUID snapshotId, String ruleSetVersion, String scoreVersion, ScoreCalculator.Score score,
                         List<String> unavailableSections, String summary) {
        Optional<Report> existing = findByInvestigation(organizationId, investigationId);
        if (existing.isPresent()) {
            return existing.get().id();
        }
        UUID id = UUID.randomUUID();
        for (int attempt = 0; ; attempt++) {
            try {
                Integer version = jdbc.queryForObject("select coalesce(max(report_version), 0) + 1 from reports where claim_id = ?", Integer.class, claimId);
                jdbc.update("""
                        insert into reports(id, organization_id, claim_id, investigation_id, snapshot_id, report_version, completeness, unavailable_sections, summary, score, score_band,
                                            recommended_action, components, score_version, rule_set_version)
                        values (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?::jsonb, ?, ?)
                        """, id, organizationId, claimId, investigationId, snapshotId, version, unavailableSections.isEmpty() ? "COMPLETE" : "PARTIAL", Json.write(unavailableSections),
                        summary, score.total(), score.band(), score.recommendedAction(), Json.write(ScoreCalculator.toMaps(score.components())), scoreVersion, ruleSetVersion);
                break;
            } catch (DuplicateKeyException e) {
                if (attempt >= 3) {
                    throw e;
                }
            }
        }
        audit.record(organizationId, claimId, investigationId, null, "REPORT_GENERATED", Map.of("reportId", id.toString(), "score", score.total(), "band", score.band(),
                "recommendedAction", score.recommendedAction(), "partial", !unavailableSections.isEmpty()));
        return id;
    }

    @Transactional(readOnly = true)
    public Optional<Report> findByInvestigation(UUID organizationId, UUID investigationId) {
        return query("where organization_id = ? and investigation_id = ?", organizationId, investigationId);
    }

    @Transactional(readOnly = true)
    public Optional<Report> latestForClaim(UUID organizationId, UUID claimId) {
        return query("where organization_id = ? and claim_id = ? order by report_version desc limit 1", organizationId, claimId);
    }

    private Optional<Report> query(String where, Object... args) {
        return jdbc.query("select id, created_at, investigation_id, claim_id, snapshot_id, report_version, completeness, unavailable_sections::text, summary, score, score_band, recommended_action, components::text, score_version, rule_set_version from reports " + where,
                (rs, n) -> new Report(rs.getObject(1, UUID.class), rs.getTimestamp(2).toInstant(), rs.getObject(3, UUID.class), rs.getObject(4, UUID.class), rs.getObject(5, UUID.class), rs.getInt(6),
                        rs.getString(7), Json.strings(rs.getString(8)), rs.getString(9), rs.getInt(10), rs.getString(11), rs.getString(12), Json.list(rs.getString(13)), rs.getString(14), rs.getString(15)), args).stream().findFirst();
    }

    /**
     * A report is stale when evidence has arrived since its snapshot was frozen: a document, a reading of a document, or a newer import of
     * history. The report is never edited; the investigator investigates again.
     */
    @Transactional(readOnly = true)
    public boolean isStale(UUID organizationId, Report report, Instant snapshotCreatedAt) {
        Timestamp since = Timestamp.from(snapshotCreatedAt);
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists(select 1 from documents where organization_id = ? and claim_id = ? and received_at > ?)
                    or exists(select 1 from document_extractions where organization_id = ? and claim_id = ? and created_at > ?)
                    or exists(select 1 from historical_imports where organization_id = ? and import_type = 'HISTORICAL_CLAIMS' and status in ('COMPLETED', 'COMPLETED_WITH_ERRORS') and finished_at > ?)
                """, Boolean.class, organizationId, report.claimId(), since, organizationId, report.claimId(), since, organizationId, since));
    }
}
