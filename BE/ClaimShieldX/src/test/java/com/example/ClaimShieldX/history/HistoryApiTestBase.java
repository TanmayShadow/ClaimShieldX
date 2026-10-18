package com.example.ClaimShieldX.history;

import com.example.ClaimShieldX.support.DbFixtures;
import com.example.ClaimShieldX.support.TestActors;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/** Real database and real HTTP stack. Not @Transactional: imports commit in several transactions, so everything is cleaned up by hand. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class HistoryApiTestBase {
    @Autowired protected MockMvc mockMvc;
    @Autowired protected JdbcTemplate jdbc;
    @Value("${app.auth.jwt-secret}") protected String jwtSecret;

    protected DbFixtures fixtures;

    @BeforeEach
    void historySetUp() {
        fixtures = new DbFixtures(jdbc);
    }

    @AfterEach
    void historyCleanUp() {
        fixtures.cleanUp();
    }

    protected TestActors admin() {
        return TestActors.create(fixtures, jwtSecret, "ORG_ADMIN");
    }

    protected TestActors investigator() {
        return TestActors.create(fixtures, jwtSecret, "INVESTIGATOR");
    }

    protected TestActors adminOf(TestActors other) {
        return TestActors.inOrganization(fixtures, jwtSecret, other.organizationId(), "ORG_ADMIN");
    }

    protected static String newKey() {
        return "key-" + UUID.randomUUID();
    }

    protected ResultActions importCsv(TestActors actor, String csv, String importType, String key) throws Exception {
        return importBytes(actor, csv.getBytes(StandardCharsets.UTF_8), importType, "1", key, null);
    }

    protected ResultActions importCsv(TestActors actor, String csv, String importType) throws Exception {
        return importCsv(actor, csv, importType, newKey());
    }

    protected ResultActions importBytes(TestActors actor, byte[] bytes, String importType, String templateVersion, String key, String source) throws Exception {
        var request = multipart(HttpMethod.POST, "/api/v1/historical-imports")
                .file(new MockMultipartFile("file", "history.csv", "text/csv", bytes))
                .param("importType", importType)
                .param("templateVersion", templateVersion)
                .header("Authorization", actor.bearer());
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        if (source != null) {
            request.param("source", source);
        }
        return mockMvc.perform(request);
    }

    /** Imports a claims file and returns the new import's id, failing the test if the request itself was refused. */
    protected UUID importClaimsOk(TestActors actor, String csv) throws Exception {
        String body = importCsv(actor, csv, "HISTORICAL_CLAIMS").andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    protected UUID importGaragesOk(TestActors actor, String csv) throws Exception {
        String body = importCsv(actor, csv, "GARAGE_HISTORY").andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    protected ResultActions getImport(TestActors actor, UUID importId) throws Exception {
        return mockMvc.perform(get("/api/v1/historical-imports/" + importId).header("Authorization", actor.bearer()));
    }

    protected ResultActions getRows(TestActors actor, UUID importId, String... params) throws Exception {
        var request = get("/api/v1/historical-imports/" + importId + "/rows").header("Authorization", actor.bearer());
        for (int i = 0; i < params.length; i += 2) {
            request = request.param(params[i], params[i + 1]);
        }
        return mockMvc.perform(request);
    }

    protected ResultActions matches(TestActors actor, UUID claimId) throws Exception {
        return mockMvc.perform(get("/api/v1/claims/" + claimId + "/history-matches").header("Authorization", actor.bearer()));
    }

    protected int count(String table, UUID organizationId) {
        return jdbc.queryForObject("select count(*) from " + table + " where organization_id = ?", Integer.class, organizationId);
    }

    protected UUID claimWith(TestActors actor, String internalId, String vehicle, String policy, String claimant, String garage, Instant incidentAt) {
        return fixtures.claimWithTokens(actor.organizationId(), internalId, vehicle, policy, claimant, garage, incidentAt);
    }
}
