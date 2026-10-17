package com.example.ClaimShieldX.claims;

import com.example.ClaimShieldX.auth.model.UserPrincipal;
import com.example.ClaimShieldX.claims.dto.*;
import com.example.ClaimShieldX.claims.mapper.ClaimMapper;
import com.example.ClaimShieldX.claims.model.Claim;
import com.example.ClaimShieldX.claims.model.ClaimStage;
import com.example.ClaimShieldX.organization.Organization;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ClaimMapperTests {
    private final ClaimMapper mapper = new ClaimMapper();

    private final UUID organizationId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UserPrincipal principal = principal();

    private UserPrincipal principal() {
        Organization organization = new Organization();
        organization.setId(organizationId);
        organization.setName("Org");
        return new UserPrincipal(userId, "user@test.local", "x", organization);
    }

    private ClaimCreationRequest request(String internalId, ClaimantDto claimant) {
        return new ClaimCreationRequest(
                internalId, 3L, ClaimStage.FINAL_INVOICE_RECEIVED,
                new PolicyDto("POL-1", "COMPREHENSIVE", Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-12-31T00:00:00Z")),
                claimant,
                new GarageDto("G-1"),
                new IncidentDto(Instant.parse("2026-09-10T05:00:00Z"), Instant.parse("2026-09-11T03:30:00Z"), "CITY"),
                new FinancialDto(45000L, 40000L, "INR", 1000L));
    }

    @Test
    void toEntityCopiesEveryRequestField() {
        Claim claim = mapper.toEntity(request("CLM-1", new ClaimantDto("CLT-1")), principal);

        assertThat(claim.getInternalId()).isEqualTo("CLM-1");
        assertThat(claim.getClaimVersion()).isEqualTo(3L);
        assertThat(claim.getCurrentStage()).isEqualTo(ClaimStage.FINAL_INVOICE_RECEIVED);
        assertThat(claim.getPolicy().getPolicyToken()).isEqualTo("POL-1");
        assertThat(claim.getPolicy().getPolicyType()).isEqualTo("COMPREHENSIVE");
        assertThat(claim.getPolicy().getPolicyStartDate()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(claim.getPolicy().getPolicyEndDate()).isEqualTo(Instant.parse("2026-12-31T00:00:00Z"));
        assertThat(claim.getClaimant().getClaimantToken()).isEqualTo("CLT-1");
        assertThat(claim.getGarage().getGarageId()).isEqualTo("G-1");
        assertThat(claim.getIncident().getIncidentAt()).isEqualTo(Instant.parse("2026-09-10T05:00:00Z"));
        assertThat(claim.getIncident().getReportedAt()).isEqualTo(Instant.parse("2026-09-11T03:30:00Z"));
        assertThat(claim.getIncident().getPermittedLocationPrecision()).isEqualTo("CITY");
        assertThat(claim.getFinancial().getInitialEstimateAmount()).isEqualTo(45000L);
        assertThat(claim.getFinancial().getApprovedEstimateAmount()).isEqualTo(40000L);
        assertThat(claim.getFinancial().getCurrency()).isEqualTo("INR");
        assertThat(claim.getFinancial().getDeductible()).isEqualTo(1000L);
    }

    @Test
    void organizationAndCreatorComeFromThePrincipal() {
        Instant before = Instant.now();
        Claim claim = mapper.toEntity(request("CLM-1", null), principal);

        assertThat(claim.getOrganizationId()).isEqualTo(organizationId);
        assertThat(claim.getWorkflow().getCreatedBy()).isEqualTo(userId.toString());
        assertThat(claim.getWorkflow().getCreatedAt()).isBetween(before, Instant.now());
        assertThat(claim.getWorkflow().getUpdatedAt()).isEqualTo(claim.getWorkflow().getCreatedAt());
        assertThat(claim.getWorkflow().getStatus()).isNull();
    }

    @Test
    void internalIdIsStrippedSoWhitespaceVariantsCannotCreateTwoClaims() {
        Claim claim = mapper.toEntity(request("  CLM-1 \t", null), principal);
        assertThat(claim.getInternalId()).isEqualTo("CLM-1");
    }

    @Test
    void absentClaimantStaysAbsent() {
        Claim claim = mapper.toEntity(request("CLM-1", null), principal);
        assertThat(claim.getClaimant()).isNull();
        assertThat(mapper.toResponse(claim).claimant()).isNull();
    }

    @Test
    void responseCarriesNestedDataBack() {
        Claim claim = mapper.toEntity(request("CLM-1", new ClaimantDto("CLT-1")), principal);
        ClaimResponse response = mapper.toResponse(claim);

        assertThat(response.internalId()).isEqualTo("CLM-1");
        assertThat(response.claimVersion()).isEqualTo(3L);
        assertThat(response.currentStage()).isEqualTo(ClaimStage.FINAL_INVOICE_RECEIVED);
        assertThat(response.policy().policyToken()).isEqualTo("POL-1");
        assertThat(response.claimant().claimantToken()).isEqualTo("CLT-1");
        assertThat(response.garage().garageId()).isEqualTo("G-1");
        assertThat(response.incident().permittedLocationPrecision()).isEqualTo("CITY");
        assertThat(response.financial().currency()).isEqualTo("INR");
    }

    @Test
    void responseDoesNotExposeInternalFields() {
        assertThat(Arrays.stream(ClaimResponse.class.getRecordComponents()).map(RecordComponent::getName))
                .doesNotContain("organizationId", "version", "isActive", "createdBy", "workflow");
    }
}
