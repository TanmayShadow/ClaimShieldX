package com.example.ClaimShieldX.claims;

import com.example.ClaimShieldX.support.DbFixtures;
import com.example.ClaimShieldX.support.TestActors;
import com.example.ClaimShieldX.support.TestTokens;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Not @Transactional on purpose: rows are committed through plain SQL and removed afterwards. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ClaimLookupTests {
    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Value("${app.auth.jwt-secret}") private String jwtSecret;

    private DbFixtures fixtures;

    @BeforeEach
    void setUp() {
        fixtures = new DbFixtures(jdbc);
    }

    @AfterEach
    void cleanUp() {
        fixtures.cleanUp();
    }

    @Test
    void returnsTheCallersClaim() throws Exception {
        TestActors actor = TestActors.create(fixtures, jwtSecret, "INVESTIGATOR");
        UUID claim = fixtures.claim(actor.organizationId(), "CLM-LOOKUP-1");

        mockMvc.perform(get("/api/v1/claims/{id}", claim).header("Authorization", actor.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(claim.toString()))
                .andExpect(jsonPath("$.internalId").value("CLM-LOOKUP-1"))
                .andExpect(jsonPath("$.currentStage").value("FINAL_INVOICE_RECEIVED"))
                .andExpect(jsonPath("$.vehicle.vehicleToken").value("VEH-1"))
                .andExpect(jsonPath("$.incident.incidentType").value("COLLISION"))
                .andExpect(jsonPath("$.organizationId").doesNotExist())
                .andExpect(jsonPath("$.isActive").doesNotExist());
    }

    @Test
    void bothRolesCanRead() throws Exception {
        TestActors admin = TestActors.create(fixtures, jwtSecret, "ORG_ADMIN");
        UUID claim = fixtures.claim(admin.organizationId(), "CLM-LOOKUP-2");
        TestActors investigator = TestActors.inOrganization(fixtures, jwtSecret, admin.organizationId(), "INVESTIGATOR");

        for (TestActors reader : new TestActors[]{admin, investigator}) {
            mockMvc.perform(get("/api/v1/claims/{id}", claim).header("Authorization", reader.bearer())).andExpect(status().isOk());
        }
    }

    @Test
    void anotherOrganizationsClaimLooksExactlyLikeAMissingOne() throws Exception {
        TestActors owner = TestActors.create(fixtures, jwtSecret, "INVESTIGATOR");
        TestActors intruder = TestActors.create(fixtures, jwtSecret, "ORG_ADMIN");
        UUID claim = fixtures.claim(owner.organizationId(), "CLM-LOOKUP-3");

        String foreign = mockMvc.perform(get("/api/v1/claims/{id}", claim).header("Authorization", intruder.bearer()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND")).andReturn().getResponse().getContentAsString();
        String missing = mockMvc.perform(get("/api/v1/claims/{id}", UUID.randomUUID()).header("Authorization", intruder.bearer()))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();

        // Same shape, nothing to tell the two apart except the timestamp.
        org.assertj.core.api.Assertions.assertThat(foreign.replaceAll("\"timestamp\":\"[^\"]+\"", "")).isEqualTo(missing.replaceAll("\"timestamp\":\"[^\"]+\"", ""));
    }

    @Test
    void aMalformedIdIsABadRequest() throws Exception {
        TestActors actor = TestActors.create(fixtures, jwtSecret, "INVESTIGATOR");

        mockMvc.perform(get("/api/v1/claims/{id}", "not-a-uuid").header("Authorization", actor.bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("claimId")));
    }

    @Test
    void readingRequiresALogin() throws Exception {
        TestActors actor = TestActors.create(fixtures, jwtSecret, "INVESTIGATOR");
        UUID claim = fixtures.claim(actor.organizationId(), "CLM-LOOKUP-4");

        mockMvc.perform(get("/api/v1/claims/{id}", claim)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(get("/api/v1/claims/{id}", claim).header("Authorization", "Bearer " + TestTokens.expired(jwtSecret, actor.email())))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"));
    }
}
