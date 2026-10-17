package com.example.ClaimShieldX.claims;

import com.example.ClaimShieldX.auth.model.UserPrincipal;
import com.example.ClaimShieldX.auth.model.UserRole;
import com.example.ClaimShieldX.claims.dto.*;
import com.example.ClaimShieldX.claims.mapper.ClaimMapper;
import com.example.ClaimShieldX.claims.model.Claim;
import com.example.ClaimShieldX.claims.model.ClaimStage;
import com.example.ClaimShieldX.claims.model.IncidentType;
import com.example.ClaimShieldX.organization.Organization;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
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
        return new UserPrincipal(userId, "user@test.local", "x", organization, UserRole.INVESTIGATOR);
    }

    private ClaimCreationRequest request(String internalId, ClaimantDto claimant) {
        return request(internalId, claimant, new VehicleDto("VEH-1", "DEMO_MAKE", "DEMO_MODEL"),
                new FinancialDto(new BigDecimal("45000.5"), new BigDecimal("40000"), "INR", new BigDecimal("1000.00")));
    }

    private ClaimCreationRequest request(String internalId, ClaimantDto claimant, VehicleDto vehicle, FinancialDto financial) {
        return new ClaimCreationRequest(
                internalId, 3L, ClaimStage.FINAL_INVOICE_RECEIVED,
                new PolicyDto("POL-1", "COMPREHENSIVE", Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-12-31T00:00:00Z")),
                claimant,
                new GarageDto("G-1"),
                vehicle,
                new IncidentDto(Instant.parse("2026-09-10T05:00:00Z"), Instant.parse("2026-09-11T03:30:00Z"), IncidentType.COLLISION, "CITY"),
                financial);
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
        assertThat(claim.getVehicle().getVehicleToken()).isEqualTo("VEH-1");
        assertThat(claim.getVehicle().getMake()).isEqualTo("DEMO_MAKE");
        assertThat(claim.getVehicle().getModel()).isEqualTo("DEMO_MODEL");
        assertThat(claim.getIncident().getIncidentAt()).isEqualTo(Instant.parse("2026-09-10T05:00:00Z"));
        assertThat(claim.getIncident().getReportedAt()).isEqualTo(Instant.parse("2026-09-11T03:30:00Z"));
        assertThat(claim.getIncident().getIncidentType()).isEqualTo(IncidentType.COLLISION);
        assertThat(claim.getIncident().getPermittedLocationPrecision()).isEqualTo("CITY");
        assertThat(claim.getFinancial().getInitialEstimateAmount()).isEqualByComparingTo("45000.50");
        assertThat(claim.getFinancial().getApprovedEstimateAmount()).isEqualByComparingTo("40000");
        assertThat(claim.getFinancial().getCurrency()).isEqualTo("INR");
        assertThat(claim.getFinancial().getDeductible()).isEqualByComparingTo("1000");
    }

    @Test
    void moneyIsNormalisedToExactlyTwoDecimalsWithoutChangingTheValue() {
        Claim claim = mapper.toEntity(request("CLM-1", null, new VehicleDto("VEH-1", null, null),
                new FinancialDto(new BigDecimal("45000"), new BigDecimal("0.1"), "INR", new BigDecimal("1E+3"))), principal);

        assertThat(claim.getFinancial().getInitialEstimateAmount().toPlainString()).isEqualTo("45000.00");
        assertThat(claim.getFinancial().getApprovedEstimateAmount().toPlainString()).isEqualTo("0.10");
        assertThat(claim.getFinancial().getDeductible().toPlainString()).isEqualTo("1000.00");
    }

    @Test
    void absentAmountsStayAbsentNotZero() {
        Claim claim = mapper.toEntity(request("CLM-1", null, new VehicleDto("VEH-1", null, null),
                new FinancialDto(null, null, "INR", null)), principal);

        assertThat(claim.getFinancial().getInitialEstimateAmount()).isNull();
        assertThat(claim.getFinancial().getApprovedEstimateAmount()).isNull();
        assertThat(claim.getFinancial().getDeductible()).isNull();
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
    void vehicleTokenIsStrippedButOtherwiseKeptExactly() {
        Claim claim = mapper.toEntity(request("CLM-1", null, new VehicleDto("  Veh-Token-1a \n", null, null),
                new FinancialDto(null, null, "INR", null)), principal);
        assertThat(claim.getVehicle().getVehicleToken()).isEqualTo("Veh-Token-1a");
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
        assertThat(response.vehicle().vehicleToken()).isEqualTo("VEH-1");
        assertThat(response.vehicle().make()).isEqualTo("DEMO_MAKE");
        assertThat(response.incident().incidentType()).isEqualTo(IncidentType.COLLISION);
        assertThat(response.incident().permittedLocationPrecision()).isEqualTo("CITY");
        assertThat(response.financial().currency()).isEqualTo("INR");
        assertThat(response.financial().initialEstimateAmount().toPlainString()).isEqualTo("45000.50");
    }

    @Test
    void responseDoesNotExposeInternalFields() {
        assertThat(Arrays.stream(ClaimResponse.class.getRecordComponents()).map(RecordComponent::getName))
                .doesNotContain("organizationId", "version", "isActive", "createdBy", "workflow");
    }
}
