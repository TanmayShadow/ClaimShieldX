package com.example.ClaimShieldX.claims;

import com.example.ClaimShieldX.claims.dto.*;
import com.example.ClaimShieldX.claims.model.ClaimStage;
import com.example.ClaimShieldX.claims.model.IncidentType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ClaimCreationRequestValidationTests {
    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private static final Instant INCIDENT = Instant.parse("2026-09-10T05:00:00Z");

    private static ClaimCreationRequest request(String internalId, Long claimVersion, ClaimStage stage,
                                                PolicyDto policy, ClaimantDto claimant, GarageDto garage,
                                                VehicleDto vehicle, IncidentDto incident, FinancialDto financial) {
        return new ClaimCreationRequest(internalId, claimVersion, stage, policy, claimant, garage, vehicle, incident, financial);
    }

    private static PolicyDto policy() {
        return new PolicyDto("POL-1", "COMPREHENSIVE", Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-12-31T00:00:00Z"));
    }

    private static VehicleDto vehicle() {
        return new VehicleDto("VEH-1", "DEMO_MAKE", "DEMO_MODEL");
    }

    private static IncidentDto incident() {
        return new IncidentDto(INCIDENT, INCIDENT.plusSeconds(3600), IncidentType.COLLISION, "CITY");
    }

    private static FinancialDto financial() {
        return new FinancialDto(new BigDecimal("45000.50"), null, "INR", new BigDecimal("1000"));
    }

    private static FinancialDto financial(BigDecimal initial, BigDecimal approved, String currency, BigDecimal deductible) {
        return new FinancialDto(initial, approved, currency, deductible);
    }

    private static ClaimCreationRequest valid() {
        return request("CLM-1", 1L, ClaimStage.FINAL_INVOICE_RECEIVED, policy(), new ClaimantDto("CLT-1"),
                new GarageDto("G-1"), vehicle(), incident(), financial());
    }

    private static ClaimCreationRequest withFinancial(FinancialDto financial) {
        return request("CLM-1", 1L, ClaimStage.FNOL, policy(), null, new GarageDto("G-1"), vehicle(), incident(), financial);
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
                new GarageDto("G-1"), vehicle(), incident(), financial());
        assertThat(VALIDATOR.validate(request)).isEmpty();
    }

    @Test
    void vehicleMakeAndModelAreOptional() {
        ClaimCreationRequest request = request("CLM-1", 1L, ClaimStage.FNOL, policy(), null,
                new GarageDto("G-1"), new VehicleDto("VEH-1", null, null), incident(), financial());
        assertThat(VALIDATOR.validate(request)).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void blankInternalIdIsRejected(String internalId) {
        ClaimCreationRequest request = request(internalId, 1L, ClaimStage.FNOL, policy(), null,
                new GarageDto("G-1"), vehicle(), incident(), financial());
        assertThat(violatedFields(request)).containsExactly("internalId");
    }

    @Test
    void internalIdLongerThanColumnIsRejected() {
        ClaimCreationRequest request = request("x".repeat(256), 1L, ClaimStage.FNOL, policy(), null,
                new GarageDto("G-1"), vehicle(), incident(), financial());
        assertThat(violatedFields(request)).containsExactly("internalId");
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, -5})
    void nonPositiveClaimVersionIsRejected(long version) {
        ClaimCreationRequest request = request("CLM-1", version, ClaimStage.FNOL, policy(), null,
                new GarageDto("G-1"), vehicle(), incident(), financial());
        assertThat(violatedFields(request)).containsExactly("claimVersion");
    }

    @Test
    void missingClaimVersionAndStageAreRejected() {
        ClaimCreationRequest request = request("CLM-1", null, null, policy(), null,
                new GarageDto("G-1"), vehicle(), incident(), financial());
        assertThat(violatedFields(request)).containsExactlyInAnyOrder("claimVersion", "currentStage");
    }

    @Test
    void missingRequiredBlocksAreRejected() {
        ClaimCreationRequest request = request("CLM-1", 1L, ClaimStage.FNOL, null, null, null, null, null, null);
        assertThat(violatedFields(request)).containsExactlyInAnyOrder("policy", "garage", "vehicle", "incident", "financial");
    }

    @Test
    void requiredFieldsInsideBlocksAreRejected() {
        ClaimCreationRequest request = request("CLM-1", 1L, ClaimStage.FNOL,
                new PolicyDto(" ", null, null, null), null,
                new GarageDto(""),
                new VehicleDto("  ", null, null),
                new IncidentDto(null, null, null, null),
                financial(null, null, null, null));
        assertThat(violatedFields(request)).containsExactlyInAnyOrder(
                "policy.policyToken", "garage.garageId", "vehicle.vehicleToken",
                "incident.incidentAt", "incident.incidentType", "financial.currency");
    }

    @ParameterizedTest
    @ValueSource(strings = {"inr", "IN", "INRR", "IN1", " ", "₹"})
    void currencyMustBeThreeUppercaseLetters(String currency) {
        assertThat(violatedFields(withFinancial(financial(BigDecimal.ONE, null, currency, null)))).contains("financial.currency");
    }

    @Test
    void negativeAmountsAreRejected() {
        FinancialDto negative = financial(new BigDecimal("-1"), new BigDecimal("-0.01"), "INR", new BigDecimal("-3"));
        assertThat(violatedFields(withFinancial(negative))).containsExactlyInAnyOrder(
                "financial.initialEstimateAmount", "financial.approvedEstimateAmount", "financial.deductible");
    }

    @Test
    void zeroAmountsAreAccepted() {
        FinancialDto zero = financial(BigDecimal.ZERO, new BigDecimal("0.00"), "INR", BigDecimal.ZERO);
        assertThat(VALIDATOR.validate(withFinancial(zero))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.001", "45000.123", "1.999", "0.0001"})
    void moreThanTwoDecimalsIsRejectedNotRounded(String amount) {
        FinancialDto tooPrecise = financial(new BigDecimal(amount), null, "INR", null);
        assertThat(violatedFields(withFinancial(tooPrecise))).containsExactly("financial.initialEstimateAmount");
    }

    @ParameterizedTest
    @ValueSource(strings = {"45000", "45000.5", "45000.50", "45000.500", "0.10", "0.2", "9999999999999.99", "1E+3"})
    void amountsWithAtMostTwoSignificantDecimalsAreAccepted(String amount) {
        FinancialDto fine = financial(new BigDecimal(amount), null, "INR", null);
        assertThat(VALIDATOR.validate(withFinancial(fine))).isEmpty();
    }

    @Test
    void amountsLargerThanTheColumnAreRejected() {
        FinancialDto tooBig = financial(new BigDecimal("10000000000000.00"), null, "INR", null);
        assertThat(violatedFields(withFinancial(tooBig))).containsExactly("financial.initialEstimateAmount");
    }

    @Test
    void overlongOptionalTextIsRejected() {
        ClaimCreationRequest request = request("CLM-1", 1L, ClaimStage.FNOL,
                new PolicyDto("POL-1", "x".repeat(101), null, null), new ClaimantDto("x".repeat(256)),
                new GarageDto("G-1"), new VehicleDto("x".repeat(256), "x".repeat(101), "x".repeat(101)),
                new IncidentDto(INCIDENT, null, IncidentType.OTHER, "x".repeat(101)), financial());
        assertThat(violatedFields(request)).containsExactlyInAnyOrder(
                "policy.policyType", "claimant.claimantToken", "vehicle.vehicleToken", "vehicle.make", "vehicle.model",
                "incident.permittedLocationPrecision");
    }

    // Date inconsistencies are signals for the rules module to flag, not reasons to reject a claim at intake.
    @Test
    void dateInconsistenciesAreNotRejectedAtIntake() {
        PolicyDto endBeforeStart = new PolicyDto("POL-1", null, Instant.parse("2026-12-31T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"));
        IncidentDto reportedBeforeIncident = new IncidentDto(INCIDENT, INCIDENT.minusSeconds(86400), IncidentType.THEFT, null);
        ClaimCreationRequest request = request("CLM-1", 1L, ClaimStage.FNOL, endBeforeStart, null,
                new GarageDto("G-1"), vehicle(), reportedBeforeIncident, financial());
        assertThat(VALIDATOR.validate(request)).isEmpty();
    }
}
