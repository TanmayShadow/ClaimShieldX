package com.example.ClaimShieldX.documents.service;

import com.example.ClaimShieldX.documents.model.DocumentType;
import com.example.ClaimShieldX.documents.storage.DocumentStorage;
import com.example.ClaimShieldX.exceptions.StorageObjectNotFoundException;
import com.example.ClaimShieldX.exceptions.StorageUnavailableException;
import com.example.ClaimShieldX.support.DbFixtures;
import com.example.ClaimShieldX.support.TestActors;
import com.example.ClaimShieldX.support.TestFiles;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Injects failures into the two things an upload depends on (the file store and the database row) to prove a
 * failure never leaves a row pointing at a missing file, never leaves an orphaned file when it can be avoided,
 * and never leaves the client's Idempotency-Key stuck. Lives in this package to reach the package-private persistence class.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DocumentUploadFailureTests {
    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Value("${app.auth.jwt-secret}") private String jwtSecret;
    @MockitoSpyBean private DocumentStorage storage;
    @MockitoSpyBean private DocumentPersistence persistence;
    @Autowired private PlatformTransactionManager transactionManager;

    private TransactionTemplate requiresNew;
    private DbFixtures fixtures;
    private TestActors actor;
    private UUID claim;

    @BeforeEach
    void setUp() {
        requiresNew = new TransactionTemplate(transactionManager);
        requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        fixtures = new DbFixtures(jdbc);
        actor = TestActors.create(fixtures, jwtSecret, "INVESTIGATOR");
        claim = fixtures.claim(actor.organizationId(), "CLM-" + UUID.randomUUID());
    }

    @AfterEach
    void cleanUp() {
        reset(storage, persistence);
        for (String objectKey : jdbc.queryForList("select object_key from documents where organization_id = ?", String.class, actor.organizationId())) {
            storage.delete(objectKey);
        }
        fixtures.cleanUp();
    }

    private ResultActions upload(byte[] content, String key) throws Exception {
        return mockMvc.perform(multipart(HttpMethod.POST, "/api/v1/claims/{claimId}/documents", claim)
                .file(new MockMultipartFile("file", "invoice.pdf", "application/pdf", content))
                .param("documentType", "FINAL_INVOICE").header("Idempotency-Key", key).header("Authorization", actor.bearer()));
    }

    private int documents() {
        return jdbc.queryForObject("select count(*) from documents where claim_id = ?", Integer.class, claim);
    }

    private int keyRecords(String key) {
        return jdbc.queryForObject("select count(*) from idempotency_keys where idempotency_key = ?", Integer.class, key);
    }

    private static DataIntegrityViolationException violationOf(String constraint) {
        return new DataIntegrityViolationException("could not execute statement",
                new ConstraintViolationException("violation", new SQLException("x"), constraint));
    }

    @Test
    void aFileStoreOutageGivesA503WithNoRowAndNoStuckKey() throws Exception {
        String key = "key-" + UUID.randomUUID();
        doThrow(new StorageUnavailableException("disk exploded: /secret/path", new IOException("boom")))
                .when(storage).put(any(), any(), anyLong(), any());

        upload(TestFiles.pdf(1), key)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"))
                .andExpect(content().string(not(containsString("secret"))))
                .andExpect(content().string(not(containsString("disk exploded"))));

        assertThat(documents()).isZero();
        assertThat(keyRecords(key)).as("the key must be released so the client can retry").isZero();
    }

    @Test
    void theSameRequestSucceedsOnRetryOnceTheStoreRecovers() throws Exception {
        String key = "key-" + UUID.randomUUID();
        byte[] content = TestFiles.pdf(2);
        doThrow(new StorageUnavailableException("down", new IOException())).when(storage).put(any(), any(), anyLong(), any());
        upload(content, key).andExpect(status().isServiceUnavailable());

        reset(storage);

        upload(content, key).andExpect(status().isCreated());
        assertThat(documents()).isEqualTo(1);
    }

    @Test
    void ifTheRowCannotBeWrittenTheStoredFileIsRemoved() throws Exception {
        String key = "key-" + UUID.randomUUID();
        doThrow(new DataIntegrityViolationException("database said no")).when(persistence).persist(any(), any());

        upload(TestFiles.pdf(3), key)
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(content().string(not(containsString("database said no"))));

        ArgumentCaptor<String> storedKey = ArgumentCaptor.forClass(String.class);
        verify(storage).put(storedKey.capture(), any(), anyLong(), any());
        assertThatThrownBy(() -> storage.open(storedKey.getValue())).as("no orphaned file").isInstanceOf(StorageObjectNotFoundException.class);
        assertThat(documents()).isZero();
        assertThat(keyRecords(key)).isZero();
    }

    @Test
    void ifEvenTheCleanupFailsTheClientStillSeesTheOriginalErrorNotTheCleanupError() throws Exception {
        String key = "key-" + UUID.randomUUID();
        doThrow(new DataIntegrityViolationException("database said no")).when(persistence).persist(any(), any());
        doThrow(new StorageUnavailableException("cleanup failed", new IOException())).when(storage).delete(any());

        upload(TestFiles.pdf(4), key)
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));

        assertThat(documents()).isZero();
        assertThat(keyRecords(key)).as("the key is still released").isZero();

        // The known limitation: when cleanup itself fails the file is orphaned (and logged for reconciliation).
        ArgumentCaptor<String> orphanKey = ArgumentCaptor.forClass(String.class);
        verify(storage).put(orphanKey.capture(), any(), anyLong(), any());
        reset(storage);
        try (java.io.InputStream orphan = storage.open(orphanKey.getValue())) {
            assertThat(orphan.readAllBytes()).isNotEmpty();
        }
        storage.delete(orphanKey.getValue());
    }

    @Test
    void twoUploadsOfTheSameFileThatRacePastTheCheckEndAsOneDuplicate() throws Exception {
        byte[] content = TestFiles.pdf(5);
        UUID winner = UUID.randomUUID();
        doAnswer(invocation -> {
            // The competing request commits its row first, then ours hits the unique index.
            String sha = com.example.ClaimShieldX.documents.service.FileInspector.inspect(new MockMultipartFile("f", content)).sha256();
            // The stub runs inside the persistence transaction, which is about to roll back. A real competing
            // request commits in its own transaction, so the winner is inserted in a separate one.
            requiresNew.executeWithoutResult(status -> jdbc.update("""
                    insert into documents(id, organization_id, claim_id, document_type, file_name, content_type, size_bytes, sha256, object_key, uploaded_by)
                    values (?, ?, ?, 'FINAL_INVOICE', 'f.pdf', 'application/pdf', ?, ?, ?, ?)
                    """, winner, actor.organizationId(), claim, content.length, sha, "obj/" + winner, actor.userId()));
            throw violationOf("uq_documents_active_checksum");
        }).when(persistence).persist(any(), any());

        String key = "key-" + UUID.randomUUID();
        upload(content, key)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_DOCUMENT"))
                .andExpect(jsonPath("$.details.existingDocumentId").value(winner.toString()));

        ArgumentCaptor<String> storedKey = ArgumentCaptor.forClass(String.class);
        verify(storage).put(storedKey.capture(), any(), anyLong(), any());
        assertThatThrownBy(() -> storage.open(storedKey.getValue())).isInstanceOf(StorageObjectNotFoundException.class);
        assertThat(documents()).isEqualTo(1);
        assertThat(keyRecords(key)).isZero();
    }

    @Test
    void twoRevisionsOfTheSameDocumentThatRaceEndInAnInvalidTransition() throws Exception {
        doThrow(violationOf("uq_documents_superseded_once")).when(persistence).persist(any(), any());

        upload(TestFiles.pdf(6), "key-" + UUID.randomUUID())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
        assertThat(documents()).isZero();
    }

    @Test
    void aDatabaseErrorThatIsNotADuplicateIsNeverReportedAsOne() throws Exception {
        doThrow(violationOf("fk_documents_claim")).when(persistence).persist(any(), any());

        upload(TestFiles.pdf(7), "key-" + UUID.randomUUID())
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
    }

    @Test
    void validationFailuresNeverTouchTheFileStore() throws Exception {
        upload(TestFiles.windowsExecutable(), "key-" + UUID.randomUUID()).andExpect(status().isUnsupportedMediaType());
        upload(new byte[0], "key-" + UUID.randomUUID()).andExpect(status().isBadRequest());

        verify(storage, never()).put(any(), any(), anyLong(), any());
        verifyNoInteractions(persistence);
    }

    @Test
    void aMissingClaimNeverTouchesTheFileStoreOrTheKeyTable() throws Exception {
        String key = "key-" + UUID.randomUUID();

        mockMvc.perform(multipart(HttpMethod.POST, "/api/v1/claims/{claimId}/documents", UUID.randomUUID())
                        .file(new MockMultipartFile("file", "invoice.pdf", "application/pdf", TestFiles.pdf(8)))
                        .param("documentType", DocumentType.FINAL_INVOICE.name()).header("Idempotency-Key", key).header("Authorization", actor.bearer()))
                .andExpect(status().isNotFound());

        verify(storage, never()).put(any(), any(), anyLong(), any());
        assertThat(keyRecords(key)).isZero();
    }
}
