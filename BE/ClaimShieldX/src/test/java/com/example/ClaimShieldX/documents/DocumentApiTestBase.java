package com.example.ClaimShieldX.documents;

import com.example.ClaimShieldX.documents.storage.DocumentStorage;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/** Not @Transactional on purpose: uploads touch storage and several transactions, so everything is committed and cleaned up. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class DocumentApiTestBase {
    @Autowired protected MockMvc mockMvc;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected DocumentStorage storage;
    @Value("${app.auth.jwt-secret}") protected String jwtSecret;
    @Value("${app.storage.local-path}") protected String storagePath;

    protected DbFixtures fixtures;

    @BeforeEach
    void baseSetUp() {
        fixtures = new DbFixtures(jdbc);
    }

    @AfterEach
    void baseCleanUp() {
        for (UUID organization : fixtures.organizations()) {
            for (String objectKey : jdbc.queryForList("select object_key from documents where organization_id = ?", String.class, organization)) {
                storage.delete(objectKey);
            }
        }
        fixtures.cleanUp();
    }

    protected TestActors actor() {
        return TestActors.create(fixtures, jwtSecret, "INVESTIGATOR");
    }

    protected TestActors actor(String role) {
        return TestActors.create(fixtures, jwtSecret, role);
    }

    protected UUID claimFor(TestActors actor) {
        return fixtures.claim(actor.organizationId(), "CLM-" + UUID.randomUUID());
    }

    protected static String newKey() {
        return "key-" + UUID.randomUUID();
    }

    protected static MockMultipartFile file(String name, String declaredType, byte[] content) {
        return new MockMultipartFile("file", name, declaredType, content);
    }

    protected static MockMultipartFile pdf(byte[] content) {
        return file("invoice.pdf", "application/pdf", content);
    }

    protected ResultActions upload(TestActors actor, UUID claimId, MockMultipartFile file, String documentType, String idempotencyKey) throws Exception {
        return mockMvc.perform(multipart(HttpMethod.POST, "/api/v1/claims/{claimId}/documents", claimId)
                .file(file)
                .param("documentType", documentType)
                .header("Idempotency-Key", idempotencyKey)
                .header("Authorization", actor.bearer()));
    }

    protected ResultActions upload(TestActors actor, UUID claimId, MockMultipartFile file, String documentType) throws Exception {
        return upload(actor, claimId, file, documentType, newKey());
    }

    protected UUID uploadOk(TestActors actor, UUID claimId, MockMultipartFile file, String documentType) throws Exception {
        MvcResult result = upload(actor, claimId, file, documentType).andReturn();
        if (result.getResponse().getStatus() != 201) {
            throw new AssertionError("Upload failed with HTTP " + result.getResponse().getStatus() + ": " + result.getResponse().getContentAsString());
        }
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }

    protected String objectKeyOf(UUID documentId) {
        return jdbc.queryForObject("select object_key from documents where id = ?", String.class, documentId);
    }

    protected int documentCount(UUID claimId) {
        return jdbc.queryForObject("select count(*) from documents where claim_id = ?", Integer.class, claimId);
    }
}
