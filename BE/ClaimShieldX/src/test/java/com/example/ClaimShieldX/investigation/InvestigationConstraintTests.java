package com.example.ClaimShieldX.investigation;

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

/** The rules for investigation rows that the database enforces itself, whatever any worker or API call does. */
@SpringBootTest
@ActiveProfiles("test")
class InvestigationConstraintTests {
    @Autowired JdbcTemplate jdbc;
    DbFixtures fixtures;
    UUID organization;
    UUID user;
    UUID claim;

    @BeforeEach
    void setUp() {
        fixtures = new DbFixtures(jdbc);
        organization = fixtures.organization();
        user = fixtures.user(organization);
        claim = fixtures.claim(organization, "CLM-CONSTRAINTS");
    }

    @AfterEach
    void cleanUp() {
        fixtures.cleanUp();
    }

    private UUID investigation(UUID claimId, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into investigations(id, organization_id, claim_id, claim_version, requested_by, status, attempt, max_attempts) values (?, ?, ?, 1, ?, ?, 1, 3)
                """, id, organization, claimId, user, status);
        return id;
    }

    private UUID active(UUID claimId) {
        return investigation(claimId, "QUEUED");
    }

    /** A COMPLETED, CANCELLED or FAILED investigation, with the finish time (and for FAILED the reason) the table requires. */
    private UUID finished(UUID claimId, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into investigations(id, organization_id, claim_id, claim_version, requested_by, status, attempt, max_attempts, finished_at, error_code)
                values (?, ?, ?, 1, ?, ?, 1, 3, now(), ?)
                """, id, organization, claimId, user, status, status.equals("FAILED") ? "INTERNAL_ERROR" : null);
        return id;
    }

    @Test
    void aClaimHasAtMostOneActiveInvestigation() {
        active(claim);

        assertThatThrownBy(() -> active(claim)).isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("uq_investigations_one_active_per_claim");
        assertThatThrownBy(() -> investigation(claim, "EXTRACTING")).hasMessageContaining("uq_investigations_one_active_per_claim");
        assertThatThrownBy(() -> investigation(claim, "WAITING_FOR_EVIDENCE")).hasMessageContaining("uq_investigations_one_active_per_claim");
        assertThatThrownBy(() -> investigation(claim, "RETRY_WAIT")).hasMessageContaining("uq_investigations_one_active_per_claim");
    }

    @Test
    void finishedInvestigationsDoNotCountAsActiveAndAnyNumberMayExist() {
        finished(claim, "COMPLETED");
        finished(claim, "COMPLETED");
        finished(claim, "CANCELLED");
        finished(claim, "FAILED");

        active(claim);

        assertThat(jdbc.queryForObject("select count(*) from investigations where claim_id = ?", Integer.class, claim)).isEqualTo(5);
    }

    @Test
    void differentClaimsMayEachHaveAnActiveInvestigation() {
        active(claim);
        active(fixtures.claim(organization, "CLM-OTHER"));
    }

    @Test
    void theStatusIsFromTheFixedList() {
        assertThatThrownBy(() -> investigation(claim, "RUNNING")).hasMessageContaining("ck_investigations_status");
        assertThatThrownBy(() -> investigation(claim, "queued")).hasMessageContaining("ck_investigations_status");
    }

    @Test
    void attemptsMustBePositive() {
        assertThatThrownBy(() -> jdbc.update("""
                insert into investigations(id, organization_id, claim_id, claim_version, requested_by, status, attempt, max_attempts) values (?, ?, ?, 1, ?, 'QUEUED', 0, 3)
                """, UUID.randomUUID(), organization, claim, user)).hasMessageContaining("ck_investigations_attempts");
        assertThatThrownBy(() -> jdbc.update("""
                insert into investigations(id, organization_id, claim_id, claim_version, requested_by, status, attempt, max_attempts) values (?, ?, ?, 1, ?, 'QUEUED', 1, 0)
                """, UUID.randomUUID(), organization, claim, user)).hasMessageContaining("ck_investigations_attempts");
    }

    @Test
    void aLeaseNeedsBothAnOwnerAndAnExpiry() {
        UUID id = active(claim);

        assertThatThrownBy(() -> jdbc.update("update investigations set lease_owner = 'w1' where id = ?", id)).hasMessageContaining("ck_investigations_lease");
        assertThatThrownBy(() -> jdbc.update("update investigations set lease_expires_at = now() where id = ?", id)).hasMessageContaining("ck_investigations_lease");
        jdbc.update("update investigations set lease_owner = 'w1', lease_expires_at = now() where id = ?", id);
    }

    @Test
    void aFinishedInvestigationHoldsNoLeaseAndHasAFinishTime() {
        UUID id = active(claim);

        assertThatThrownBy(() -> jdbc.update("update investigations set status = 'COMPLETED', finished_at = now(), lease_owner = 'w1', lease_expires_at = now() where id = ?", id))
                .hasMessageContaining("ck_investigations_terminal_lease");
        assertThatThrownBy(() -> jdbc.update("update investigations set status = 'COMPLETED' where id = ?", id)).hasMessageContaining("ck_investigations_finished");
        assertThatThrownBy(() -> jdbc.update("update investigations set finished_at = now() where id = ?", id)).hasMessageContaining("ck_investigations_finished");
    }

    @Test
    void aFailedInvestigationSaysWhy() {
        UUID id = active(claim);

        assertThatThrownBy(() -> jdbc.update("update investigations set status = 'FAILED', finished_at = now() where id = ?", id)).hasMessageContaining("ck_investigations_failed_reason");
        jdbc.update("update investigations set status = 'FAILED', finished_at = now(), error_code = 'TIMEOUT' where id = ?", id);
    }

    @Test
    void anInvestigationMustBelongToAClaimOfItsOwnOrganization() {
        UUID other = fixtures.organization();

        assertThatThrownBy(() -> jdbc.update("""
                insert into investigations(id, organization_id, claim_id, claim_version, requested_by, status, attempt, max_attempts) values (?, ?, ?, 1, ?, 'QUEUED', 1, 3)
                """, UUID.randomUUID(), other, claim, user)).hasMessageContaining("fk_investigations_claim");
    }

    @Test
    void theRuleRunAnInvestigationRecordsMustBeOfTheSameClaim() {
        UUID otherClaim = fixtures.claim(organization, "CLM-OTHER");
        UUID run = UUID.randomUUID();
        jdbc.update("""
                insert into rule_runs(id, organization_id, claim_id, rule_set_version, config, config_sha256, input_fingerprint, inputs, pass_count, flag_count, incomplete_count, not_applicable_count, error_count)
                values (?, ?, ?, '1', '{}'::jsonb, ?, ?, '{}'::jsonb, 0, 0, 0, 0, 0)
                """, run, organization, otherClaim, "a".repeat(64), "b".repeat(64));
        UUID id = active(claim);

        assertThatThrownBy(() -> jdbc.update("update investigations set rule_run_id = ? where id = ?", run, id)).hasMessageContaining("fk_investigations_rule_run");
    }

    @Test
    void aStageHasOneRowPerInvestigationFromTheFixedLists() {
        UUID id = active(claim);
        jdbc.update("insert into investigation_stages(id, investigation_id, organization_id, claim_id, stage, status) values (?, ?, ?, ?, 'EXTRACTING', 'RUNNING')",
                UUID.randomUUID(), id, organization, claim);

        assertThatThrownBy(() -> jdbc.update("insert into investigation_stages(id, investigation_id, organization_id, claim_id, stage, status) values (?, ?, ?, ?, 'EXTRACTING', 'OK')",
                UUID.randomUUID(), id, organization, claim)).hasMessageContaining("uq_investigation_stages_stage");
        assertThatThrownBy(() -> jdbc.update("insert into investigation_stages(id, investigation_id, organization_id, claim_id, stage, status) values (?, ?, ?, ?, 'PACKING', 'OK')",
                UUID.randomUUID(), id, organization, claim)).hasMessageContaining("ck_investigation_stages_stage");
        assertThatThrownBy(() -> jdbc.update("insert into investigation_stages(id, investigation_id, organization_id, claim_id, stage, status) values (?, ?, ?, ?, 'RUNNING_RULES', 'DONE')",
                UUID.randomUUID(), id, organization, claim)).hasMessageContaining("ck_investigation_stages_status");
    }

    @Test
    void aStageMustCarryItsInvestigationsClaimAndOrganization() {
        UUID id = active(claim);
        UUID otherClaim = fixtures.claim(organization, "CLM-OTHER");

        assertThatThrownBy(() -> jdbc.update("insert into investigation_stages(id, investigation_id, organization_id, claim_id, stage, status) values (?, ?, ?, ?, 'EXTRACTING', 'RUNNING')",
                UUID.randomUUID(), id, organization, otherClaim)).hasMessageContaining("fk_investigation_stages_investigation");
    }

    @Test
    void aClaimWithInvestigationsCannotBeDeleted() {
        active(claim);

        assertThatThrownBy(() -> jdbc.update("delete from claims where id = ?", claim)).isInstanceOf(DataIntegrityViolationException.class);
    }
}
