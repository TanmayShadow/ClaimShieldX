package com.example.ClaimShieldX.claims;

import com.example.ClaimShieldX.auth.model.UserPrincipal;
import com.example.ClaimShieldX.auth.model.UserRole;
import com.example.ClaimShieldX.claims.dto.*;
import com.example.ClaimShieldX.claims.mapper.ClaimMapper;
import com.example.ClaimShieldX.claims.model.Claim;
import com.example.ClaimShieldX.claims.model.ClaimStage;
import com.example.ClaimShieldX.claims.model.IncidentType;
import com.example.ClaimShieldX.claims.repository.ClaimsRepository;
import com.example.ClaimShieldX.claims.service.ClaimsService;
import com.example.ClaimShieldX.exceptions.DuplicateClaimException;
import com.example.ClaimShieldX.organization.Organization;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ClaimsServiceTests {
    private final UUID organizationId = UUID.randomUUID();

    private ClaimsRepository repository;
    private ClaimsService service;
    private UserPrincipal principal;

    @BeforeEach
    void setUp() {
        repository = mock(ClaimsRepository.class);
        service = new ClaimsService(repository, new ClaimMapper());
        Organization organization = new Organization();
        organization.setId(organizationId);
        principal = new UserPrincipal(UUID.randomUUID(), "user@test.local", "x", organization, UserRole.INVESTIGATOR);
    }

    private ClaimCreationRequest request(String internalId) {
        return new ClaimCreationRequest(internalId, 1L, ClaimStage.FNOL,
                new PolicyDto("POL-1", null, null, null), null, new GarageDto("G-1"),
                new VehicleDto("VEH-1", null, null),
                new IncidentDto(Instant.parse("2026-09-10T05:00:00Z"), null, IncidentType.COLLISION, null),
                new FinancialDto(null, null, "INR", null));
    }

    private DataIntegrityViolationException violation(String constraintName) {
        return new DataIntegrityViolationException("could not execute statement",
                new ConstraintViolationException("violation", new SQLException("x"), constraintName));
    }

    @Test
    void createsClaimScopedToTheCallersOrganization() {
        when(repository.existsByOrganizationIdAndInternalId(organizationId, "CLM-1")).thenReturn(false);
        when(repository.saveAndFlush(any(Claim.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ClaimResponse response = service.createClaim(request("CLM-1"), principal);

        assertThat(response.internalId()).isEqualTo("CLM-1");
        verify(repository).existsByOrganizationIdAndInternalId(organizationId, "CLM-1");
        verify(repository).saveAndFlush(any(Claim.class));
    }

    @Test
    void rejectsDuplicateInSameOrganizationWithoutSaving() {
        when(repository.existsByOrganizationIdAndInternalId(organizationId, "CLM-1")).thenReturn(true);

        assertThatThrownBy(() -> service.createClaim(request("CLM-1"), principal))
                .isInstanceOf(DuplicateClaimException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void duplicateCheckUsesTheStrippedId() {
        when(repository.existsByOrganizationIdAndInternalId(organizationId, "CLM-1")).thenReturn(true);

        assertThatThrownBy(() -> service.createClaim(request("  CLM-1  "), principal))
                .isInstanceOf(DuplicateClaimException.class);
    }

    @Test
    void lostRaceOnTheUniqueConstraintBecomesDuplicate() {
        when(repository.existsByOrganizationIdAndInternalId(any(), any())).thenReturn(false);
        when(repository.saveAndFlush(any(Claim.class))).thenThrow(violation("uq_claims_org_internal_id"));

        assertThatThrownBy(() -> service.createClaim(request("CLM-1"), principal))
                .isInstanceOf(DuplicateClaimException.class);
    }

    @Test
    void otherIntegrityViolationsAreNotMistakenForDuplicates() {
        when(repository.existsByOrganizationIdAndInternalId(any(), any())).thenReturn(false);
        when(repository.saveAndFlush(any(Claim.class))).thenThrow(violation("fk_claims_organization"));

        assertThatThrownBy(() -> service.createClaim(request("CLM-1"), principal))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void integrityViolationWithoutConstraintDetailsIsRethrown() {
        when(repository.existsByOrganizationIdAndInternalId(any(), any())).thenReturn(false);
        when(repository.saveAndFlush(any(Claim.class))).thenThrow(new DataIntegrityViolationException("no cause"));

        assertThatThrownBy(() -> service.createClaim(request("CLM-1"), principal))
                .isInstanceOf(DataIntegrityViolationException.class);
        verify(repository, times(1)).saveAndFlush(any());
    }
}
