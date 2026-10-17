package com.example.ClaimShieldX.extraction;

import com.example.ClaimShieldX.documents.model.DocumentType;
import com.example.ClaimShieldX.support.DbFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** What the database itself refuses, so a bug in the application cannot store a wrong extraction record. */
@SpringBootTest
@ActiveProfiles("test")
class ExtractionConstraintTests {
    @Autowired private JdbcTemplate jdbc;

    private DbFixtures fixtures;
    private UUID organization;
    private UUID claim;
    private UUID document;

    @BeforeEach
    void setUp() {
        fixtures = new DbFixtures(jdbc);
        organization = fixtures.organization();
        UUID user = fixtures.user(organization);
        claim = fixtures.claim(organization, "CLM-X");
        document = fixtures.document(organization, claim, user, DocumentType.FINAL_INVOICE, DbFixtures.sha256());
    }

    @AfterEach
    void cleanUp() {
        fixtures.cleanUp();
    }

    private void insert(UUID org, UUID claimId, UUID documentId, String type, int attempt, String status, String failureCode, String result) {
        jdbc.update("""
                insert into document_extractions(id, organization_id, claim_id, document_id, document_type, attempt, status, schema_version,
                                                 prompt_version, model_id, failure_code, result)
                values (?, ?, ?, ?, ?, ?, ?, 'itemized-cost-1', '1', 'gemini-3.5-flash-lite', ?, ?::jsonb)
                """, UUID.randomUUID(), org, claimId, documentId, type, attempt, status, failureCode, result);
    }

    @Test
    void aSuccessWithAResultAndAFailureWithACodeAreAccepted() {
        assertThatCode(() -> insert(organization, claim, document, "FINAL_INVOICE", 1, "SUCCEEDED", null, "{\"fields\":[]}")).doesNotThrowAnyException();
        assertThatCode(() -> insert(organization, claim, document, "FINAL_INVOICE", 2, "FAILED", "MODEL_UNAVAILABLE", null)).doesNotThrowAnyException();
    }

    @Test
    void anOutcomeMustBeConsistent() {
        assertThatThrownBy(() -> insert(organization, claim, document, "FINAL_INVOICE", 1, "SUCCEEDED", null, null))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_document_extractions_outcome");
        assertThatThrownBy(() -> insert(organization, claim, document, "FINAL_INVOICE", 1, "FAILED", null, null))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_document_extractions_outcome");
        assertThatThrownBy(() -> insert(organization, claim, document, "FINAL_INVOICE", 1, "FAILED", "INTERNAL_ERROR", "{}"))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_document_extractions_outcome");
        assertThatThrownBy(() -> insert(organization, claim, document, "FINAL_INVOICE", 1, "SUCCEEDED", "INTERNAL_ERROR", "{}"))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_document_extractions_outcome");
    }

    @Test
    void anAttemptNumberIsUniquePerDocumentAndPositive() {
        insert(organization, claim, document, "FINAL_INVOICE", 1, "SUCCEEDED", null, "{}");

        assertThatThrownBy(() -> insert(organization, claim, document, "FINAL_INVOICE", 1, "SUCCEEDED", null, "{}"))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("uq_document_extractions_attempt");
        assertThatThrownBy(() -> insert(organization, claim, document, "FINAL_INVOICE", 0, "SUCCEEDED", null, "{}"))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_document_extractions_attempt");
    }

    @Test
    void anUnknownStatusIsRefused() {
        // Refused by the status check and, independently, by the outcome check: either one is enough.
        assertThatThrownBy(() -> insert(organization, claim, document, "FINAL_INVOICE", 1, "VERIFIED", null, "{}"))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("document_extractions");
    }

    @Test
    void theRecordMustBelongToTheDocumentsOwnClaimAndType() {
        UUID otherOrganization = fixtures.organization();
        UUID otherClaim = fixtures.claim(otherOrganization, "CLM-OTHER");

        assertThatThrownBy(() -> insert(organization, otherClaim, document, "FINAL_INVOICE", 1, "SUCCEEDED", null, "{}"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insert(otherOrganization, otherClaim, document, "FINAL_INVOICE", 1, "SUCCEEDED", null, "{}"))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("fk_document_extractions_document");
        assertThatThrownBy(() -> insert(organization, claim, document, "GARAGE_ESTIMATE", 1, "SUCCEEDED", null, "{}"))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("fk_document_extractions_document");
    }

    @Test
    void theNewestAttemptIsFoundByTheIndexedQuery() {
        insert(organization, claim, document, "FINAL_INVOICE", 1, "FAILED", "MODEL_UNAVAILABLE", null);
        insert(organization, claim, document, "FINAL_INVOICE", 2, "SUCCEEDED", null, "{}");

        Integer latest = jdbc.queryForObject("select attempt from document_extractions where document_id = ? order by attempt desc limit 1", Integer.class, document);

        assertThat(latest).isEqualTo(2);
    }
}
