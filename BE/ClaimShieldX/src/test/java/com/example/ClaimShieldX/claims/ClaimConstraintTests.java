package com.example.ClaimShieldX.claims;

import com.example.ClaimShieldX.auth.model.UserPrincipal;
import com.example.ClaimShieldX.auth.model.UserRole;
import com.example.ClaimShieldX.claims.dto.*;
import com.example.ClaimShieldX.claims.model.ClaimStage;
import com.example.ClaimShieldX.claims.model.IncidentType;
import com.example.ClaimShieldX.claims.service.ClaimsService;
import com.example.ClaimShieldX.exceptions.DuplicateClaimException;
import com.example.ClaimShieldX.organization.Organization;
import com.example.ClaimShieldX.organization.OrganizationRepository;
import com.example.ClaimShieldX.support.DbFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Deliberately not @Transactional: these tests commit, so they verify real database behaviour
 * (constraints and concurrent transactions). They remove everything they create.
 */
@SpringBootTest
@ActiveProfiles("test")
class ClaimConstraintTests {
    private static final int PARALLEL_REQUESTS = 8;

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private ClaimsService claimsService;

    private DbFixtures fixtures;

    @BeforeEach
    void setUp() {
        fixtures = new DbFixtures(jdbcTemplate);
    }

    @AfterEach
    void cleanUp() {
        fixtures.cleanUp();
    }

    private void insert(UUID organizationId, String internalId, String stage, String vehicleToken, String incidentType,
                        String currency, BigDecimal initialEstimate, long claimVersion) {
        jdbcTemplate.update("""
                insert into claims(id, organization_id, internal_id, claim_version, current_stage, policy_token, garage_id,
                                   vehicle_token, incident_type, incident_at, currency, initial_estimate_amount)
                values (?, ?, ?, ?, ?, 'POL-1', 'G-1', ?, ?, now(), ?, ?)
                """, UUID.randomUUID(), organizationId, internalId, claimVersion, stage, vehicleToken, incidentType, currency, initialEstimate);
    }

    private void insertValid(UUID organizationId, String internalId) {
        insert(organizationId, internalId, "FINAL_INVOICE_RECEIVED", "VEH-1", "COLLISION", "INR", null, 1);
    }

    @Test
    void databaseRejectsSameInternalIdInSameOrganization() {
        UUID organization = fixtures.organization();
        insertValid(organization, "CLM-1");

        assertThatThrownBy(() -> insertValid(organization, "CLM-1"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_claims_org_internal_id");
    }

    @Test
    void databaseAllowsSameInternalIdInDifferentOrganizations() {
        insertValid(fixtures.organization(), "CLM-1");
        insertValid(fixtures.organization(), "CLM-1");
    }

    @Test
    void databaseRejectsNullInternalId() {
        UUID organization = fixtures.organization();

        assertThatThrownBy(() -> insertValid(organization, null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("internal_id");
    }

    @Test
    void databaseRejectsClaimForUnknownOrganization() {
        assertThatThrownBy(() -> insertValid(UUID.randomUUID(), "CLM-1"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_claims_organization");
    }

    @Test
    void databaseRejectsMissingVehicleToken() {
        UUID organization = fixtures.organization();

        assertThatThrownBy(() -> insert(organization, "CLM-1", "FNOL", null, "COLLISION", "INR", null, 1))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("vehicle_token");
    }

    @Test
    void databaseRejectsMissingIncidentType() {
        UUID organization = fixtures.organization();

        assertThatThrownBy(() -> insert(organization, "CLM-1", "FNOL", "VEH-1", null, "INR", null, 1))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("incident_type");
    }

    // incident_type is validated by the Java enum, not the database, so a new category needs no migration (decision D3).
    @Test
    void databaseDoesNotRestrictIncidentTypeValues() {
        UUID organization = fixtures.organization();

        assertThatCode(() -> insert(organization, "CLM-1", "FNOL", "VEH-1", "FIRE", "INR", null, 1)).doesNotThrowAnyException();
    }

    @Test
    void databaseRejectsUnknownStage() {
        UUID organization = fixtures.organization();

        assertThatThrownBy(() -> insert(organization, "CLM-1", "NOT_A_STAGE", "VEH-1", "COLLISION", "INR", null, 1))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_claims_current_stage");
    }

    @Test
    void databaseRejectsClaimVersionBelowOne() {
        UUID organization = fixtures.organization();

        assertThatThrownBy(() -> insert(organization, "CLM-1", "FNOL", "VEH-1", "COLLISION", "INR", null, 0))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_claims_claim_version");
    }

    @Test
    void databaseRejectsMalformedCurrency() {
        UUID organization = fixtures.organization();

        assertThatThrownBy(() -> insert(organization, "CLM-1", "FNOL", "VEH-1", "COLLISION", "inr", null, 1))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_claims_currency");
    }

    @Test
    void databaseRejectsNegativeAmounts() {
        UUID organization = fixtures.organization();

        assertThatThrownBy(() -> insert(organization, "CLM-1", "FNOL", "VEH-1", "COLLISION", "INR", new BigDecimal("-0.01"), 1))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_claims_amounts_non_negative");
    }

    @Test
    void moneyColumnsHoldPaise() {
        for (String column : List.of("initial_estimate_amount", "approved_estimate_amount", "deductible")) {
            assertThat(jdbcTemplate.queryForObject(
                    "select numeric_precision || ',' || numeric_scale from information_schema.columns where table_name = 'claims' and column_name = ?",
                    String.class, column)).as(column).isEqualTo("15,2");
        }
        UUID organization = fixtures.organization();
        insert(organization, "CLM-1", "FNOL", "VEH-1", "COLLISION", "INR", new BigDecimal("18500.55"), 1);
        assertThat(jdbcTemplate.queryForObject("select initial_estimate_amount from claims where organization_id = ?",
                BigDecimal.class, organization)).isEqualByComparingTo("18500.55");
    }

    @Test
    void currencyHasNoSilentUsdDefault() {
        assertThat(jdbcTemplate.queryForObject(
                "select column_default from information_schema.columns where table_name = 'claims' and column_name = 'currency'",
                String.class)).isNull();
    }

    @Test
    void vehicleLookupIndexAndTenantKeyExist() {
        assertThat(jdbcTemplate.queryForObject("select count(*) from pg_indexes where indexname = 'idx_claims_org_vehicle_token'", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from pg_constraint where conname = 'uq_claims_id_organization'", Integer.class)).isEqualTo(1);
    }

    @Test
    void parallelRequestsForTheSameIdCreateExactlyOneClaim() throws Exception {
        Organization organization = new Organization();
        organization.setName("Org-" + UUID.randomUUID());
        organizationRepository.save(organization);
        UUID organizationId = organization.getId();
        try {
            UserPrincipal principal = new UserPrincipal(UUID.randomUUID(), "user@test.local", "x", organization, UserRole.INVESTIGATOR);
            ClaimCreationRequest request = request("CLM-RACE");

            ExecutorService pool = Executors.newFixedThreadPool(PARALLEL_REQUESTS);
            try {
                CountDownLatch ready = new CountDownLatch(PARALLEL_REQUESTS);
                CountDownLatch go = new CountDownLatch(1);
                List<Future<String>> outcomes = new ArrayList<>();
                for (int i = 0; i < PARALLEL_REQUESTS; i++) {
                    outcomes.add(pool.submit(() -> {
                        ready.countDown();
                        go.await();
                        try {
                            claimsService.createClaim(request, principal);
                            return "CREATED";
                        } catch (DuplicateClaimException e) {
                            return "DUPLICATE";
                        }
                    }));
                }
                ready.await();
                go.countDown();

                List<String> results = new ArrayList<>();
                for (Future<String> outcome : outcomes) {
                    results.add(outcome.get(30, TimeUnit.SECONDS));
                }

                assertThat(results).filteredOn("CREATED"::equals).hasSize(1);
                assertThat(results).filteredOn("DUPLICATE"::equals).hasSize(PARALLEL_REQUESTS - 1);
            } finally {
                pool.shutdownNow();
            }

            assertThat(jdbcTemplate.queryForObject(
                    "select count(*) from claims where organization_id = ? and internal_id = 'CLM-RACE'",
                    Integer.class, organizationId)).isEqualTo(1);
        } finally {
            jdbcTemplate.update("delete from claims where organization_id = ?", organizationId);
            jdbcTemplate.update("delete from organization where id = ?", organizationId);
        }
    }

    private ClaimCreationRequest request(String internalId) {
        return new ClaimCreationRequest(internalId, 1L, ClaimStage.FINAL_INVOICE_RECEIVED,
                new PolicyDto("POL-1", null, null, null), null, new GarageDto("G-1"),
                new VehicleDto("VEH-1", null, null),
                new IncidentDto(Instant.parse("2026-09-10T05:00:00Z"), null, IncidentType.COLLISION, null),
                new FinancialDto(new BigDecimal("45000.00"), null, "INR", null));
    }
}
