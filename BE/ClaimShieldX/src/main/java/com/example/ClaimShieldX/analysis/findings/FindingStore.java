package com.example.ClaimShieldX.analysis.findings;

import com.example.ClaimShieldX.analysis.Json;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Findings of one investigation. Re-running the stage replaces them, so a retry never doubles them. */
@Service
public class FindingStore {
    public record Row(UUID id, int ordinal, String source, String category, String title, String summary, String type, String status, String validationState,
                      String rejectionReason, List<String> supportingRefs, List<String> counterRefs, List<String> counterEvidence, List<String> missingChecks, String recommendation) {
    }

    private final JdbcTemplate jdbc;

    public FindingStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void replace(UUID organizationId, UUID claimId, UUID investigationId, UUID snapshotId, List<FindingDraft> drafts) {
        jdbc.update("delete from findings where organization_id = ? and investigation_id = ?", organizationId, investigationId);
        int ordinal = 1;
        for (FindingDraft d : drafts) {
            jdbc.update("""
                    insert into findings(id, organization_id, claim_id, investigation_id, snapshot_id, ordinal, source, category, title, summary, finding_type, status, validation_state,
                                         rejection_reason, supporting_refs, counter_refs, counter_evidence, missing_checks, recommendation)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?)
                    """, UUID.randomUUID(), organizationId, claimId, investigationId, snapshotId, ordinal++, d.source(), d.category(), cut(d.title(), 200), d.summary(), d.type(), d.status(),
                    d.validationState(), d.rejectionReason(), Json.write(d.supportingRefs()), Json.write(d.counterRefs()), Json.write(d.counterEvidence()), Json.write(d.missingChecks()), d.recommendation());
        }
    }

    @Transactional(readOnly = true)
    public List<Row> list(UUID organizationId, UUID investigationId, boolean validatedOnly) {
        return jdbc.query("select id, ordinal, source, category, title, summary, finding_type, status, validation_state, rejection_reason, supporting_refs::text, counter_refs::text, counter_evidence::text, missing_checks::text, recommendation "
                        + "from findings where organization_id = ? and investigation_id = ?" + (validatedOnly ? " and validation_state = 'VALIDATED'" : "") + " order by ordinal",
                (rs, n) -> new Row(rs.getObject(1, UUID.class), rs.getInt(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9),
                        rs.getString(10), Json.strings(rs.getString(11)), Json.strings(rs.getString(12)), Json.strings(rs.getString(13)), Json.strings(rs.getString(14)), rs.getString(15)),
                organizationId, investigationId);
    }

    private static String cut(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }
}
