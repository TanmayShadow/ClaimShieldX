package com.example.ClaimShieldX.claims;

import com.example.ClaimShieldX.auth.model.UserPrincipal;
import com.example.ClaimShieldX.claims.dto.*;
import com.example.ClaimShieldX.claims.model.ClaimStage;
import com.example.ClaimShieldX.claims.service.ClaimsService;
import com.example.ClaimShieldX.exceptions.DuplicateClaimException;
import com.example.ClaimShieldX.organization.Organization;
import com.example.ClaimShieldX.organization.OrganizationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
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

    private final List<UUID> createdOrganizations = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (UUID organizationId : createdOrganizations) {
            jdbcTemplate.update("delete from claims where organization_id = ?", organizationId);
            jdbcTemplate.update("delete from organization where id = ?", organizationId);
        }
    }

    private Organization newOrganization() {
        Organization organization = new Organization();
        organization.setName("Org-" + UUID.randomUUID());
        organizationRepository.save(organization);
        createdOrganizations.add(organization.getId());
        return organization;
    }

    private void insertClaim(UUID organizationId, String internalId) {
        jdbcTemplate.update("insert into claims(id, organization_id, internal_id) values (?, ?, ?)",
                UUID.randomUUID(), organizationId, internalId);
    }

    @Test
    void databaseRejectsSameInternalIdInSameOrganization() {
        Organization organization = newOrganization();
        insertClaim(organization.getId(), "CLM-1");

        assertThatThrownBy(() -> insertClaim(organization.getId(), "CLM-1"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_claims_org_internal_id");
    }

    @Test
    void databaseAllowsSameInternalIdInDifferentOrganizations() {
        insertClaim(newOrganization().getId(), "CLM-1");
        insertClaim(newOrganization().getId(), "CLM-1");
    }

    @Test
    void databaseRejectsNullInternalId() {
        Organization organization = newOrganization();

        assertThatThrownBy(() -> insertClaim(organization.getId(), null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("internal_id");
    }

    @Test
    void databaseRejectsClaimForUnknownOrganization() {
        assertThatThrownBy(() -> insertClaim(UUID.randomUUID(), "CLM-1"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_claims_organization");
    }

    @Test
    void parallelRequestsForTheSameIdCreateExactlyOneClaim() throws Exception {
        Organization organization = newOrganization();
        UserPrincipal principal = new UserPrincipal(UUID.randomUUID(), "user@test.local", "x", organization);
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
                Integer.class, organization.getId())).isEqualTo(1);
    }

    private ClaimCreationRequest request(String internalId) {
        return new ClaimCreationRequest(internalId, 1L, ClaimStage.FINAL_INVOICE_RECEIVED,
                new PolicyDto("POL-1", null, null, null), null, new GarageDto("G-1"),
                new IncidentDto(Instant.parse("2026-09-10T05:00:00Z"), null, null),
                new FinancialDto(45000L, null, "INR", null));
    }
}
