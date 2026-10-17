package com.example.ClaimShieldX.claims;

import com.example.ClaimShieldX.claims.dto.*;
import com.example.ClaimShieldX.claims.model.ClaimStage;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ClaimCreationRequestValidationTests {
    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private static final Instant INCIDENT = Instant.parse("2026-09-10T05:00:00Z");

    private static ClaimCreationRequest request(String internalId, Long claimVersion, ClaimStage stage,
                                                PolicyDto policy, ClaimantDto claimant, GarageDto garage,
                                                IncidentDto incident, FinancialDto financial) {
        return new ClaimCreationRequest(internalId, claimVersion, stage, policy, claimant, garage, incident, financial);
    }

    private static PolicyDto policy() {
        return new PolicyDto("POL-1", "COMPREHENSIVE", Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-12-31T00:00:00Z"));
    }

    private static IncidentDto incident() {
        return new IncidentDto(INCIDENT, INCIDENT.plusSeconds(3600), "CITY");
    }

    private static FinancialDto financial() {
        return new FinancialDto(45000L, null, "INR", 1000L);
    }

    private static ClaimCreationRequest valid() {
        return request("CLM-1", 1L, ClaimStage.FINAL_INVOICE_RECEIVED, policy(), new ClaimantDto("CLT-1"),
                new GarageDto("G-1"), incident(), financial());
    }

    private static Set<String> violatedFields(ClaimCreationRequest request) {
        return VALIDATOR.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    @Test
    void validRequestHasNoViolations() {
        assertThat(VALIDATOR.validate(valid())).isEmpty();
    }

    @Test
    void claimantBlockIsOptional() {
        ClaimCreationRequest request = request("CLM-1", 1L, ClaimStage.FNOL, policy(), null,
                new GarageDto("G-1"), incident(), financial());
        assertThat(VALIDATOR.validate(request)).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void blankInternalIdIsRejected(String internalId) {
        ClaimCreationRequest request = request(internalId, 1L, ClaimStage.FNOL, policy(), null,
                new GarageDto("G-1"), incident(), financial());
        assertThat(violatedFields(request)).containsExactly("internalId");
    }

    @Test
    void internalIdLongerThanColumnIsRejected() {
        ClaimCreationRequest request = request("x".repeat(256), 1L, ClaimStage.FNOL, policy(), null,
                new GarageDto("G-1"), incident(), financial());
        assertThat(violatedFields(request)).containsExactly("internalId");
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, -5})
    void nonPositiveClaimVersionIsRejected(long version) {
        ClaimCreationRequest request = request("CLM-1", version, ClaimStage.FNOL, policy(), null,
                new GarageDto("G-1"), incident(), financial());
        assertThat(violatedFields(request)).containsExactly("claimVersion");
    }

    @Test
    void missingClaimVersionAndStageAreRejected() {
        ClaimCreationRequest request = request("CLM-1", null, null, policy(), null,
                new GarageDto("G-1"), incident(), financial());
        assertThat(violatedFields(request)).containsExactlyInAnyOrder("claimVersion", "currentStage");
    }

    @Test
    void missingRequiredBlocksAreRejected() {
        ClaimCreationRequest request = request("CLM-1", 1L, ClaimStage.FNOL, null, null, null, null, null);
        assertThat(violatedFields(request)).containsExactlyInAnyOrder("policy", "garage", "incident", "financial");
    }

    @Test
    void requiredFieldsInsideBlocksAreRejected() {
        ClaimCreationRequest request = request("CLM-1", 1L, ClaimStage.FNOL,
                new PolicyDto(" ", null, null, null), null,
                new GarageDto(""),
                new IncidentDto(null, null, null),
                new FinancialDto(null, null, null, null));
        assertThat(violatedFields(request)).containsExactlyInAnyOrder(
                "policy.policyToken", "garage.garageId", "incident.incidentAt", "financial.currency");
    }

    @ParameterizedTest
    @ValueSource(strings = {"inr", "IN", "INRR", "IN1", " ", "₹"})
    void currencyMustBeThreeUppercaseLetters(String currency) {
        ClaimCreationRequest request = request("CLM-1", 1L, ClaimStage.FNOL, policy(), null,
                new GarageDto("G-1"), incident(), new FinancialDto(1L, null, currency, null));
        assertThat(violatedFields(request)).contains("financial.currency");
    }

    @Test
    void negativeAmountsAreRejected() {
        ClaimCreationRequest request = request("CLM-1", 1L, ClaimStage.FNOL, policy(), null,
                new GarageDto("G-1"), incident(), new FinancialDto(-1L, -2L, "INR", -3L));
        assertThat(violatedFields(request)).containsExactlyInAnyOrder(
                "financial.initialEstimateAmount", "financial.approvedEstimateAmount", "financial.deductible");
    }

    @Test
    void zeroAmountsAreAccepted() {
        ClaimCreationRequest request = request("CLM-1", 1L, ClaimStage.FNOL, policy(), null,
                new GarageDto("G-1"), incident(), new FinancialDto(0L, 0L, "INR", 0L));
        assertThat(VALIDATOR.validate(request)).isEmpty();
    }

    @Test
    void overlongOptionalTextIsRejected() {
        ClaimCreationRequest request = request("CLM-1", 1L, ClaimStage.FNOL,
                new PolicyDto("POL-1", "x".repeat(101), null, null), new ClaimantDto("x".repeat(256)),
                new GarageDto("G-1"), new IncidentDto(INCIDENT, null, "x".repeat(101)), financial());
        assertThat(violatedFields(request)).containsExactlyInAnyOrder(
                "policy.policyType", "claimant.claimantToken", "incident.permittedLocationPrecision");
    }

    // Date inconsistencies are signals for the rules module to flag, not reasons to reject a claim at intake.
    @Test
    void dateInconsistenciesAreNotRejectedAtIntake() {
        PolicyDto endBeforeStart = new PolicyDto("POL-1", null, Instant.parse("2026-12-31T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"));
        IncidentDto reportedBeforeIncident = new IncidentDto(INCIDENT, INCIDENT.minusSeconds(86400), null);
        ClaimCreationRequest request = request("CLM-1", 1L, ClaimStage.FNOL, endBeforeStart, null,
                new GarageDto("G-1"), reportedBeforeIncident, financial());
        assertThat(VALIDATOR.validate(request)).isEmpty();
    }
}
