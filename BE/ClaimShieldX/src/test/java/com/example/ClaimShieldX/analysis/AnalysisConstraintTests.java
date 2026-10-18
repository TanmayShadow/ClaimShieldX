package com.example.ClaimShieldX.analysis;

import com.example.ClaimShieldX.support.DbFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** What the database refuses on its own for snapshots, analyses, findings, reports and decisions, whatever the application does. */
@SpringBootTest
@ActiveProfiles("test")
class AnalysisConstraintTests {
    @Autowired JdbcTemplate jdbc;
    DbFixtures fixtures;
    UUID organization;
    UUID user;
    UUID claim;
    UUID investigation;
    UUID snapshot;

    @BeforeEach
    void setUp() {
        fixtures = new DbFixtures(jdbc);
        jdbc.update("delete from investigation_stages");
        jdbc.update("delete from investigations");
        organization = fixtures.organization();
        user = fixtures.user(organization);
        claim = fixtures.claim(organization, "CLM-ANALYSIS-CONSTRAINTS");
        investigation = UUID.randomUUID();
        jdbc.update("""
                insert into investigations(id, organization_id, claim_id, claim_version, requested_by, status, attempt, max_attempts, finished_at)
                values (?, ?, ?, 1, ?, 'COMPLETED', 1, 3, now())
                """, investigation, organization, claim, user);
        UUID ruleRun = UUID.randomUUID();
        jdbc.update("""
                insert into rule_runs(id, organization_id, claim_id, rule_set_version, config, config_sha256, input_fingerprint, inputs, pass_count, flag_count, incomplete_count, not_applicable_count, error_count)
                values (?, ?, ?, '1', '{}'::jsonb, ?, ?, '{}'::jsonb, 0, 0, 0, 0, 0)
                """, ruleRun, organization, claim, "a".repeat(64), "b".repeat(64));
        snapshot = UUID.randomUUID();
        jdbc.update("""
                insert into evidence_snapshots(id, organization_id, claim_id, investigation_id, claim_version, rule_run_id, rule_set_version, input_fingerprint, item_count)
                values (?, ?, ?, ?, 1, ?, '1', 'fp', 0)
                """, snapshot, organization, claim, investigation, ruleRun);
    }

    @AfterEach
    void cleanUp() {
        fixtures.cleanUp();
    }

    private void item(String ref, String type, String state) {
        jdbc.update("insert into evidence_items(id, organization_id, claim_id, snapshot_id, ref, item_type, state, label, data) values (?, ?, ?, ?, ?, ?, ?, 'x', '{}'::jsonb)",
                UUID.randomUUID(), organization, claim, snapshot, ref, type, state);
    }

    private void analysis(String role, String status, String error) {
        jdbc.update("insert into investigation_analyses(id, organization_id, claim_id, investigation_id, role, status, error_code, model_id, prompt_version, model_calls, result) values (?, ?, ?, ?, ?, ?, ?, 'm', '1', 1, '{}'::jsonb)",
                UUID.randomUUID(), organization, claim, investigation, role, status, error);
    }

    private void finding(int ordinal, String type, String validation, String rejection) {
        jdbc.update("""
                insert into findings(id, organization_id, claim_id, investigation_id, snapshot_id, ordinal, source, category, title, summary, finding_type, status, validation_state, rejection_reason, supporting_refs, counter_refs, counter_evidence, missing_checks, recommendation)
                values (?, ?, ?, ?, ?, ?, 'RULE', 'C', 'T', 'S', ?, 'OPEN', ?, ?, '[]'::jsonb, '[]'::jsonb, '[]'::jsonb, '[]'::jsonb, 'r')
                """, UUID.randomUUID(), organization, claim, investigation, snapshot, ordinal, type, validation, rejection);
    }

    private UUID report(int version, int score, String band) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into reports(id, organization_id, claim_id, investigation_id, snapshot_id, report_version, completeness, unavailable_sections, summary, score, score_band, recommended_action, components, score_version, rule_set_version)
                values (?, ?, ?, ?, ?, ?, 'COMPLETE', '[]'::jsonb, 's', ?, ?, 'MANUAL_REVIEW', '[]'::jsonb, '1', '1')
                """, id, organization, claim, investigation, snapshot, version, score, band);
        return id;
    }

    @Test
    void anEvidenceReferenceIsUniqueWithinASnapshotAndOnlyKnownTypesAndStatesAreAccepted() {
        item("CLAIM.stage", "CLAIM_FIELD", "VERIFIED_FACT");
        assertThatThrownBy(() -> item("CLAIM.stage", "CLAIM_FIELD", "VERIFIED_FACT")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> item("X.1", "GUESS", "VERIFIED_FACT")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> item("X.2", "CLAIM_FIELD", "PROBABLY")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aSecondSnapshotOfTheSameInvestigationIsRefused() {
        assertThatThrownBy(() -> jdbc.update("""
                insert into evidence_snapshots(id, organization_id, claim_id, investigation_id, claim_version, rule_run_id, rule_set_version, input_fingerprint, item_count)
                select ?, organization_id, claim_id, investigation_id, claim_version, rule_run_id, rule_set_version, input_fingerprint, 0 from evidence_snapshots where id = ?
                """, UUID.randomUUID(), snapshot)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void anAnalysisIsUnavailableExactlyWhenItHasAnErrorCode() {
        assertThatCode(() -> analysis("H1", "OK", null)).doesNotThrowAnyException();
        assertThatCode(() -> analysis("H2", "UNAVAILABLE", "MODEL_UNAVAILABLE")).doesNotThrowAnyException();
        assertThatThrownBy(() -> analysis("H3", "OK", "MODEL_UNAVAILABLE")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> analysis("H3", "UNAVAILABLE", null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> analysis("H1", "OK", null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> analysis("H9", "OK", null)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aFindingIsRejectedExactlyWhenItHasARejectionReason() {
        assertThatCode(() -> finding(1, "FACT", "VALIDATED", null)).doesNotThrowAnyException();
        assertThatCode(() -> finding(2, "HYPOTHESIS", "REJECTED", "NOT_SUPPORTED_BY_EVIDENCE")).doesNotThrowAnyException();
        assertThatThrownBy(() -> finding(3, "HYPOTHESIS", "VALIDATED", "NOT_SUPPORTED_BY_EVIDENCE")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> finding(4, "HYPOTHESIS", "REJECTED", null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> finding(5, "VERDICT", "VALIDATED", null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> finding(1, "FACT", "VALIDATED", null)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aReportScoreStaysWithinZeroToOneHundredAndEachInvestigationHasOneReport() {
        assertThatThrownBy(() -> report(1, 101, "HIGH")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> report(1, -1, "LOW")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> report(1, 10, "SEVERE")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatCode(() -> report(1, 100, "HIGH")).doesNotThrowAnyException();
        assertThatThrownBy(() -> report(2, 10, "LOW")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aReviewDecisionNeedsARealActionAndARationaleOfSensibleLength() {
        UUID report = report(1, 15, "LOW");
        String insert = """
                insert into review_actions(id, organization_id, claim_id, investigation_id, report_id, snapshot_id, created_by, action, rationale, report_version, report_was_stale, recommended_action, score)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, 1, false, 'MANUAL_REVIEW', 15)
                """;
        assertThatCode(() -> jdbc.update(insert, UUID.randomUUID(), organization, claim, investigation, report, snapshot, user, "DEFER_REVIEW", "Waiting for the garage to answer")).doesNotThrowAnyException();
        assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), organization, claim, investigation, report, snapshot, user, "REJECT_CLAIM", "Waiting for the garage to answer")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), organization, claim, investigation, report, snapshot, user, "DEFER_REVIEW", "short")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), organization, claim, investigation, report, snapshot, user, "DEFER_REVIEW", "x".repeat(2001))).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("select count(*) from review_actions where investigation_id = ?", Integer.class, investigation)).isEqualTo(1);
    }

    @Test
    void anotherOrganizationCannotAttachRowsToThisInvestigation() {
        UUID other = fixtures.organization();
        assertThatThrownBy(() -> jdbc.update("""
                insert into investigation_analyses(id, organization_id, claim_id, investigation_id, role, status, model_id, prompt_version, model_calls, result)
                values (?, ?, ?, ?, 'H1', 'OK', 'm', '1', 1, '{}'::jsonb)
                """, UUID.randomUUID(), other, claim, investigation)).isInstanceOf(DataIntegrityViolationException.class);
    }
}
