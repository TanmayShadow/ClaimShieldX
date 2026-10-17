package com.example.ClaimShieldX.claims;

import com.example.ClaimShieldX.auth.model.User;
import com.example.ClaimShieldX.auth.model.UserPrincipal;
import com.example.ClaimShieldX.auth.model.UserRole;
import com.example.ClaimShieldX.auth.repository.UserRepository;
import com.example.ClaimShieldX.auth.service.TokenService;
import com.example.ClaimShieldX.organization.Organization;
import com.example.ClaimShieldX.organization.OrganizationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ClaimCreationApiTests {
    private static final String URL = "/api/v1/claims";

    @Autowired private MockMvc mockMvc;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TokenService tokenService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private record TestUser(UUID userId, UUID organizationId, String token) {
    }

    private TestUser newUser() {
        Organization organization = new Organization();
        organization.setName("Org-" + UUID.randomUUID());
        organizationRepository.save(organization);

        User user = new User();
        user.setEmail(UUID.randomUUID() + "@test.local");
        user.setPassword("not-used");
        user.setOrganization(organization);
        userRepository.save(user);

        UserPrincipal principal = new UserPrincipal(user.getId(), user.getEmail(), user.getPassword(), organization, user.getRole());
        return new TestUser(user.getId(), organization.getId(), tokenService.generateToken(principal));
    }

    private ResultActions create(TestUser user, String body) throws Exception {
        return mockMvc.perform(post(URL)
                .header("Authorization", "Bearer " + user.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private int countClaims(TestUser user, String internalId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from claims where organization_id = ? and internal_id = ?",
                Integer.class, user.organizationId(), internalId);
    }

    @Test
    void createsClaimAndPersistsEveryNestedField() throws Exception {
        TestUser user = newUser();
        String internalId = ClaimPayloads.uniqueInternalId();

        create(user, ClaimPayloads.valid(internalId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", not(emptyOrNullString())))
                .andExpect(jsonPath("$.internalId").value(internalId))
                .andExpect(jsonPath("$.claimVersion").value(1))
                .andExpect(jsonPath("$.currentStage").value("FINAL_INVOICE_RECEIVED"))
                .andExpect(jsonPath("$.policy.policyToken").value("POL-TOKEN-1001"))
                .andExpect(jsonPath("$.policy.policyType").value("COMPREHENSIVE"))
                .andExpect(jsonPath("$.claimant.claimantToken").value("CLT-TOKEN-1001"))
                .andExpect(jsonPath("$.garage.garageId").value("GARAGE-DEMO-10"))
                .andExpect(jsonPath("$.vehicle.vehicleToken").value("VEH-TOKEN-1001"))
                .andExpect(jsonPath("$.vehicle.make").value("DEMO_MAKE"))
                .andExpect(jsonPath("$.vehicle.model").value("DEMO_MODEL"))
                .andExpect(jsonPath("$.incident.incidentAt").value("2026-09-10T05:00:00Z"))
                .andExpect(jsonPath("$.incident.reportedAt").value("2026-09-11T03:30:00Z"))
                .andExpect(jsonPath("$.incident.incidentType").value("COLLISION"))
                .andExpect(jsonPath("$.incident.permittedLocationPrecision").value("CITY"))
                .andExpect(jsonPath("$.financial.initialEstimateAmount").value(45000.50))
                .andExpect(jsonPath("$.financial.currency").value("INR"))
                .andExpect(jsonPath("$.financial.deductible").value(1000))
                .andExpect(content().string(containsString("\"deductible\":1000.00")))
                .andExpect(jsonPath("$.organizationId").doesNotExist())
                .andExpect(jsonPath("$.isActive").doesNotExist())
                .andExpect(jsonPath("$.version").doesNotExist())
                .andExpect(jsonPath("$.workflow").doesNotExist());

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "select * from claims where organization_id = ? and internal_id = ?", user.organizationId(), internalId);
        assertThat(row.get("current_stage")).isEqualTo("FINAL_INVOICE_RECEIVED");
        assertThat(row.get("claim_version")).isEqualTo(1L);
        assertThat(row.get("policy_token")).isEqualTo("POL-TOKEN-1001");
        assertThat(row.get("policy_type")).isEqualTo("COMPREHENSIVE");
        assertThat(((Timestamp) row.get("policy_start_date")).toInstant()).isEqualTo(Instant.parse("2025-12-31T18:30:00Z"));
        assertThat(row.get("claimant_token")).isEqualTo("CLT-TOKEN-1001");
        assertThat(row.get("garage_id")).isEqualTo("GARAGE-DEMO-10");
        assertThat(row.get("vehicle_token")).isEqualTo("VEH-TOKEN-1001");
        assertThat(row.get("vehicle_make")).isEqualTo("DEMO_MAKE");
        assertThat(row.get("vehicle_model")).isEqualTo("DEMO_MODEL");
        assertThat(row.get("incident_type")).isEqualTo("COLLISION");
        assertThat(((Timestamp) row.get("incident_at")).toInstant()).isEqualTo(Instant.parse("2026-09-10T05:00:00Z"));
        assertThat(((Timestamp) row.get("reported_at")).toInstant()).isEqualTo(Instant.parse("2026-09-11T03:30:00Z"));
        assertThat(row.get("permitted_location_precision")).isEqualTo("CITY");
        assertThat((BigDecimal) row.get("initial_estimate_amount")).isEqualByComparingTo("45000.50");
        assertThat(row.get("approved_estimate_amount")).isNull();
        assertThat(row.get("currency")).isEqualTo("INR");
        assertThat((BigDecimal) row.get("deductible")).isEqualByComparingTo("1000.00");
        assertThat(row.get("workflow_created_by")).isEqualTo(user.userId().toString());
        assertThat(row.get("workflow_created_at")).isNotNull();
    }

    @Test
    void organizationAndCreatorAreServerControlled() throws Exception {
        TestUser caller = newUser();
        TestUser other = newUser();
        String internalId = ClaimPayloads.uniqueInternalId();
        String body = ClaimPayloads.replace(ClaimPayloads.valid(internalId), "{\n  \"internalId\"",
                "{\n  \"organizationId\": \"" + other.organizationId() + "\",\n"
                        + "  \"isActive\": false,\n"
                        + "  \"workflow\": { \"createdBy\": \"attacker\", \"status\": \"CLOSED\" },\n"
                        + "  \"internalId\"");

        create(caller, body).andExpect(status().isCreated());

        assertThat(countClaims(caller, internalId)).isEqualTo(1);
        assertThat(countClaims(other, internalId)).isZero();
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "select * from claims where organization_id = ? and internal_id = ?", caller.organizationId(), internalId);
        assertThat(row.get("is_active")).isEqualTo(true);
        assertThat(row.get("workflow_created_by")).isEqualTo(caller.userId().toString());
        assertThat(row.get("status")).isNull();
    }

    @Test
    void claimantBlockIsOptional() throws Exception {
        TestUser user = newUser();
        String internalId = ClaimPayloads.uniqueInternalId();
        String body = ClaimPayloads.replace(ClaimPayloads.valid(internalId),
                "\"claimant\": { \"claimantToken\": \"CLT-TOKEN-1001\" },", "");

        create(user, body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.claimant").doesNotExist());
        assertThat(countClaims(user, internalId)).isEqualTo(1);
    }

    @Test
    void vehicleTokenWhitespaceIsStrippedBeforeStoring() throws Exception {
        TestUser user = newUser();
        String internalId = ClaimPayloads.uniqueInternalId();
        String body = ClaimPayloads.replace(ClaimPayloads.valid(internalId), "\"vehicleToken\": \"VEH-TOKEN-1001\"", "\"vehicleToken\": \"  VEH-TOKEN-1001 \"");

        create(user, body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.vehicle.vehicleToken").value("VEH-TOKEN-1001"));
        assertThat(jdbcTemplate.queryForObject("select vehicle_token from claims where organization_id = ? and internal_id = ?",
                String.class, user.organizationId(), internalId)).isEqualTo("VEH-TOKEN-1001");
    }

    @Test
    void decimalAmountsAreStoredExactlyWithoutFloatingPointDrift() throws Exception {
        TestUser user = newUser();
        String internalId = ClaimPayloads.uniqueInternalId();
        String body = ClaimPayloads.replace(
                ClaimPayloads.replace(ClaimPayloads.valid(internalId), "\"initialEstimateAmount\": 45000.50", "\"initialEstimateAmount\": 0.10"),
                "\"deductible\": 1000", "\"deductible\": 9999999999999.99");

        create(user, body)
                .andExpect(status().isCreated())
                .andExpect(content().string(containsString("\"initialEstimateAmount\":0.10")))
                .andExpect(content().string(containsString("\"deductible\":9999999999999.99")));

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "select initial_estimate_amount, deductible from claims where organization_id = ? and internal_id = ?",
                user.organizationId(), internalId);
        assertThat(((BigDecimal) row.get("initial_estimate_amount")).toPlainString()).isEqualTo("0.10");
        assertThat(((BigDecimal) row.get("deductible")).toPlainString()).isEqualTo("9999999999999.99");
    }

    @Test
    void trailingZerosBeyondTwoDecimalsAreAcceptedButARealThirdDecimalIsNot() throws Exception {
        TestUser user = newUser();
        String internalId = ClaimPayloads.uniqueInternalId();
        String trailingZero = ClaimPayloads.replace(ClaimPayloads.valid(internalId), "\"initialEstimateAmount\": 45000.50", "\"initialEstimateAmount\": 45000.500");

        create(user, trailingZero)
                .andExpect(status().isCreated())
                .andExpect(content().string(containsString("\"initialEstimateAmount\":45000.50")));
        assertThat((BigDecimal) jdbcTemplate.queryForObject("select initial_estimate_amount from claims where organization_id = ? and internal_id = ?",
                BigDecimal.class, user.organizationId(), internalId)).isEqualByComparingTo("45000.50");

        String realThirdDecimal = ClaimPayloads.replace(ClaimPayloads.valid(), "\"initialEstimateAmount\": 45000.50", "\"initialEstimateAmount\": 45000.505");
        create(user, realThirdDecimal)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("financial.initialEstimateAmount")));
    }

    @Test
    void unauthenticatedRequestIsRejectedAndCreatesNothing() throws Exception {
        TestUser user = newUser();
        String internalId = ClaimPayloads.uniqueInternalId();

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(ClaimPayloads.valid(internalId)))
                .andExpect(status().isForbidden());

        assertThat(countClaims(user, internalId)).isZero();
    }

    static Stream<Arguments> invalidPayloads() {
        String base = ClaimPayloads.valid();
        return Stream.of(
                Arguments.of("blank internalId", ClaimPayloads.valid("   "), "internalId"),
                Arguments.of("missing claimVersion", ClaimPayloads.replace(base, "\"claimVersion\": 1,", ""), "claimVersion"),
                Arguments.of("zero claimVersion", ClaimPayloads.replace(base, "\"claimVersion\": 1", "\"claimVersion\": 0"), "claimVersion"),
                Arguments.of("negative claimVersion", ClaimPayloads.replace(base, "\"claimVersion\": 1", "\"claimVersion\": -5"), "claimVersion"),
                Arguments.of("missing currentStage", ClaimPayloads.replace(base, "\"currentStage\": \"FINAL_INVOICE_RECEIVED\",", ""), "currentStage"),
                Arguments.of("null policy", nullBlock(base, "policy"), "policy"),
                Arguments.of("null garage", nullBlock(base, "garage"), "garage"),
                Arguments.of("null incident", nullBlock(base, "incident"), "incident"),
                Arguments.of("null financial", nullBlock(base, "financial"), "financial"),
                Arguments.of("null vehicle", nullBlock(base, "vehicle"), "vehicle"),
                Arguments.of("blank vehicleToken", ClaimPayloads.replace(base, "\"vehicleToken\": \"VEH-TOKEN-1001\"", "\"vehicleToken\": \" \""), "vehicle.vehicleToken"),
                Arguments.of("missing incidentType", ClaimPayloads.replace(base, "\"incidentType\": \"COLLISION\",", ""), "incident.incidentType"),
                Arguments.of("three-decimal amount", ClaimPayloads.replace(base, "\"initialEstimateAmount\": 45000.50", "\"initialEstimateAmount\": 45000.505"), "financial.initialEstimateAmount"),
                Arguments.of("amount over 13 whole digits", ClaimPayloads.replace(base, "\"deductible\": 1000", "\"deductible\": 10000000000000"), "financial.deductible"),
                Arguments.of("blank policyToken", ClaimPayloads.replace(base, "\"policyToken\": \"POL-TOKEN-1001\"", "\"policyToken\": \" \""), "policy.policyToken"),
                Arguments.of("blank garageId", ClaimPayloads.replace(base, "\"garageId\": \"GARAGE-DEMO-10\"", "\"garageId\": \"\""), "garage.garageId"),
                Arguments.of("null incidentAt", ClaimPayloads.replace(base, "\"incidentAt\": \"2026-09-10T10:30:00+05:30\"", "\"incidentAt\": null"), "incident.incidentAt"),
                Arguments.of("lowercase currency", ClaimPayloads.replace(base, "\"currency\": \"INR\"", "\"currency\": \"inr\""), "financial.currency"),
                Arguments.of("negative estimate", ClaimPayloads.replace(base, "\"initialEstimateAmount\": 45000", "\"initialEstimateAmount\": -45000"), "financial.initialEstimateAmount")
        );
    }

    private static String nullBlock(String json, String block) {
        String result = json.replaceFirst("(?s)\"" + block + "\": \\{[^}]*\\}", "\"" + block + "\": null");
        if (result.equals(json)) {
            throw new IllegalArgumentException("Test payload has no block: " + block);
        }
        return result;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidPayloads")
    void invalidPayloadIsRejectedWithFieldLevelErrors(String name, String body, String expectedField) throws Exception {
        TestUser user = newUser();

        create(user, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.errors[*].field", hasItem(expectedField)));

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from claims where organization_id = ?", Integer.class, user.organizationId())).isZero();
    }

    @Test
    void emptyObjectReportsEveryMissingRequiredField() throws Exception {
        create(newUser(), "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItems(
                        "internalId", "claimVersion", "currentStage", "policy", "garage", "incident", "financial")));
    }

    @Test
    void validationErrorsNeverEchoTheRejectedValue() throws Exception {
        String body = ClaimPayloads.replace(ClaimPayloads.valid(), "\"currency\": \"INR\"", "\"currency\": \"zq-secret-value\"");

        create(newUser(), body)
                .andExpect(status().isBadRequest())
                .andExpect(content().string(not(containsString("zq-secret-value"))));
    }

    static Stream<Arguments> unreadablePayloads() {
        String base = ClaimPayloads.valid();
        return Stream.of(
                Arguments.of("truncated JSON", "{\"internalId\":"),
                Arguments.of("not JSON", "this is not json"),
                Arguments.of("unknown stage", ClaimPayloads.replace(base, "FINAL_INVOICE_RECEIVED", "NOT_A_STAGE")),
                Arguments.of("unparseable date", ClaimPayloads.replace(base, "2026-09-10T10:30:00+05:30", "not-a-date")),
                Arguments.of("non-numeric version", ClaimPayloads.replace(base, "\"claimVersion\": 1", "\"claimVersion\": \"abc\"")),
                Arguments.of("unknown incident type", ClaimPayloads.replace(base, "\"incidentType\": \"COLLISION\"", "\"incidentType\": \"NOT_A_TYPE\"")),
                Arguments.of("non-numeric amount", ClaimPayloads.replace(base, "\"initialEstimateAmount\": 45000.50", "\"initialEstimateAmount\": \"abc\"")),
                Arguments.of("empty body", "")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unreadablePayloads")
    void unreadablePayloadIsA400WithAUsableMessage(String name, String body) throws Exception {
        create(newUser(), body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message", not(emptyOrNullString())))
                .andExpect(jsonPath("$.timestamp", not(emptyOrNullString())));
    }

    @Test
    void duplicateInSameOrganizationIsAConflictAndStoresOnlyOneRow() throws Exception {
        TestUser user = newUser();
        String internalId = ClaimPayloads.uniqueInternalId();

        create(user, ClaimPayloads.valid(internalId)).andExpect(status().isCreated());
        create(user, ClaimPayloads.valid(internalId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message", not(emptyOrNullString())));

        assertThat(countClaims(user, internalId)).isEqualTo(1);
    }

    @Test
    void whitespaceVariantOfAnExistingIdIsAConflict() throws Exception {
        TestUser user = newUser();
        String internalId = ClaimPayloads.uniqueInternalId();

        create(user, ClaimPayloads.valid(internalId)).andExpect(status().isCreated());
        create(user, ClaimPayloads.valid("  " + internalId + "  ")).andExpect(status().isConflict());

        assertThat(countClaims(user, internalId)).isEqualTo(1);
    }

    @Test
    void sameInternalIdInADifferentOrganizationIsAllowedAndRevealsNothing() throws Exception {
        TestUser orgA = newUser();
        TestUser orgB = newUser();
        String internalId = ClaimPayloads.uniqueInternalId();

        create(orgA, ClaimPayloads.valid(internalId)).andExpect(status().isCreated());
        create(orgB, ClaimPayloads.valid(internalId)).andExpect(status().isCreated());

        assertThat(countClaims(orgA, internalId)).isEqualTo(1);
        assertThat(countClaims(orgB, internalId)).isEqualTo(1);
    }

    @Test
    void unsupportedMethodIsA405NotA500() throws Exception {
        mockMvc.perform(get(URL).header("Authorization", "Bearer " + newUser().token()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405));
    }
}
