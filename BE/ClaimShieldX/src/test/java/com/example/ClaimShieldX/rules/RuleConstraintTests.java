package com.example.ClaimShieldX.rules;

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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The rules for run and result rows that the database enforces itself, whatever the application does. */
@SpringBootTest
@ActiveProfiles("test")
class RuleConstraintTests {
    @Autowired JdbcTemplate jdbc;
    DbFixtures fixtures;
    UUID organization;
    UUID claim;

    @BeforeEach
    void setUp() {
        fixtures = new DbFixtures(jdbc);
        organization = fixtures.organization();
        claim = fixtures.claim(organization, "CLM-CONSTRAINTS");
    }

    @AfterEach
    void cleanUp() {
        fixtures.cleanUp();
    }

    private static String hex(char c) {
        return String.valueOf(c).repeat(64);
    }

    private UUID run(UUID org, UUID claimId, String config, String fingerprint) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into rule_runs(id, organization_id, claim_id, rule_set_version, config, config_sha256, input_fingerprint, inputs,
                                      pass_count, flag_count, incomplete_count, not_applicable_count, error_count)
                values (?, ?, ?, '1', '{}'::jsonb, ?, ?, '{}'::jsonb, 0, 0, 0, 0, 0)
                """, id, org, claimId, config, fingerprint);
        return id;
    }

    private void result(UUID runId, UUID org, UUID claimId, String ruleId, String outcome, String severity) {
        jdbc.update("""
                insert into rule_results(id, organization_id, claim_id, run_id, rule_id, rule_version, outcome, severity, reliability, reliability_reasons,
                                         explanation, result_values, evidence, considerations, executed_at)
                values (?, ?, ?, ?, ?, '1', ?, ?, 'RELIABLE', '[]'::jsonb, 'x', '{}'::jsonb, '[]'::jsonb, '[]'::jsonb, now())
                """, UUID.randomUUID(), org, claimId, runId, ruleId, outcome, severity);
    }

    @Test
    void aRunAndItsResultsAreAccepted() {
        UUID run = run(organization, claim, hex('a'), hex('b'));
        result(run, organization, claim, "INVOICE_ARITHMETIC", "FLAG", "REVIEW");
        result(run, organization, claim, "DATE_CONSISTENCY", "PASS", null);

        assertThat(jdbc.queryForObject("select count(*) from rule_results where run_id = ?", Integer.class, run)).isEqualTo(2);
    }

    @Test
    void identicalInputsCannotBeStoredTwiceForTheSameClaim() {
        run(organization, claim, hex('a'), hex('b'));

        assertThatThrownBy(() -> run(organization, claim, hex('a'), hex('b'))).isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("uq_rule_runs_inputs");
        run(organization, claim, hex('a'), hex('c'));
        run(organization, claim, hex('d'), hex('b'));
    }

    @Test
    void theSameFingerprintOnAnotherClaimIsFine() {
        run(organization, claim, hex('a'), hex('b'));
        UUID other = fixtures.claim(organization, "CLM-OTHER");

        run(organization, other, hex('a'), hex('b'));
    }

    @Test
    void hashesMustBeLowerCaseHexOf64Characters() {
        assertThatThrownBy(() -> run(organization, claim, "short", hex('b'))).hasMessageContaining("ck_rule_runs_hashes");
        assertThatThrownBy(() -> run(organization, claim, hex('a'), hex('B'))).hasMessageContaining("ck_rule_runs_hashes");
    }

    @Test
    void aRunMustBelongToAClaimOfTheSameOrganization() {
        UUID other = fixtures.organization();

        assertThatThrownBy(() -> run(other, claim, hex('a'), hex('b'))).isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("fk_rule_runs_claim");
    }

    @Test
    void aResultMustCarryItsRunsClaimAndOrganization() {
        UUID run = run(organization, claim, hex('a'), hex('b'));
        UUID otherClaim = fixtures.claim(organization, "CLM-OTHER");
        UUID otherOrganization = fixtures.organization();

        assertThatThrownBy(() -> result(run, organization, otherClaim, "DATE_CONSISTENCY", "PASS", null)).hasMessageContaining("fk_rule_results_run");
        assertThatThrownBy(() -> result(run, otherOrganization, claim, "DATE_CONSISTENCY", "PASS", null)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aRuleHasOneResultPerRun() {
        UUID run = run(organization, claim, hex('a'), hex('b'));
        result(run, organization, claim, "DATE_CONSISTENCY", "PASS", null);

        assertThatThrownBy(() -> result(run, organization, claim, "DATE_CONSISTENCY", "PASS", null)).hasMessageContaining("uq_rule_results_run_rule");
    }

    @Test
    void aSeverityExistsExactlyWhenSomethingWasFlagged() {
        UUID run = run(organization, claim, hex('a'), hex('b'));

        assertThatThrownBy(() -> result(run, organization, claim, "R1", "FLAG", null)).hasMessageContaining("ck_rule_results_severity_with_flag");
        assertThatThrownBy(() -> result(run, organization, claim, "R2", "PASS", "INFO")).hasMessageContaining("ck_rule_results_severity_with_flag");
        assertThatThrownBy(() -> result(run, organization, claim, "R3", "INCOMPLETE", "REVIEW")).hasMessageContaining("ck_rule_results_severity_with_flag");
    }

    @Test
    void outcomeSeverityAndReliabilityAreFromTheirFixedLists() {
        UUID run = run(organization, claim, hex('a'), hex('b'));

        assertThatThrownBy(() -> result(run, organization, claim, "R1", "FRAUD", null)).hasMessageContaining("ck_rule_results_outcome");
        assertThatThrownBy(() -> result(run, organization, claim, "R2", "FLAG", "CRITICAL")).hasMessageContaining("ck_rule_results_severity");
        assertThatThrownBy(() -> jdbc.update("""
                insert into rule_results(id, organization_id, claim_id, run_id, rule_id, rule_version, outcome, reliability, reliability_reasons, explanation, result_values, evidence, considerations, executed_at)
                values (?, ?, ?, ?, 'R3', '1', 'PASS', 'SURE', '[]'::jsonb, 'x', '{}'::jsonb, '[]'::jsonb, '[]'::jsonb, now())
                """, UUID.randomUUID(), organization, claim, run)).hasMessageContaining("ck_rule_results_reliability");
    }

    @Test
    void countsCannotBeNegative() {
        assertThatThrownBy(() -> jdbc.update("""
                insert into rule_runs(id, organization_id, claim_id, rule_set_version, config, config_sha256, input_fingerprint, inputs,
                                      pass_count, flag_count, incomplete_count, not_applicable_count, error_count)
                values (?, ?, ?, '1', '{}'::jsonb, ?, ?, '{}'::jsonb, -1, 0, 0, 0, 0)
                """, UUID.randomUUID(), organization, claim, hex('a'), hex('b'))).hasMessageContaining("ck_rule_runs_counts");
    }

    @Test
    void aRunWithResultsCannotBeDeletedWhileItsResultsExist() {
        UUID run = run(organization, claim, hex('a'), hex('b'));
        result(run, organization, claim, "DATE_CONSISTENCY", "PASS", null);

        assertThatThrownBy(() -> jdbc.update("delete from rule_runs where id = ?", run)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("delete from claims where id = ?", claim)).isInstanceOf(DataIntegrityViolationException.class);
    }
}
