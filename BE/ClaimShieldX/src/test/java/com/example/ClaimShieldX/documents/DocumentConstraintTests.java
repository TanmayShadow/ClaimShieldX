package com.example.ClaimShieldX.documents;

import com.example.ClaimShieldX.documents.model.Document;
import com.example.ClaimShieldX.documents.model.DocumentStatus;
import com.example.ClaimShieldX.documents.model.DocumentType;
import com.example.ClaimShieldX.documents.repository.DocumentRepository;
import com.example.ClaimShieldX.support.DbFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Not @Transactional on purpose: these verify committed database behaviour and clean up after themselves. */
@SpringBootTest
@ActiveProfiles("test")
class DocumentConstraintTests {
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DocumentRepository documentRepository;

    private DbFixtures fixtures;
    private UUID organization;
    private UUID user;
    private UUID claim;

    @BeforeEach
    void setUp() {
        fixtures = new DbFixtures(jdbcTemplate);
        organization = fixtures.organization();
        user = fixtures.user(organization);
        claim = fixtures.claim(organization, "CLM-1");
    }

    @AfterEach
    void cleanUp() {
        fixtures.cleanUp();
    }

    private UUID document(UUID claimId, DocumentType type, String sha256) {
        return fixtures.document(organization, claimId, user, type, sha256);
    }

    private void insertRaw(String column, Object value) {
        // Builds an insert where exactly one column carries the value under test and the rest are valid.
        UUID id = UUID.randomUUID();
        String sha = column.equals("sha256") ? (String) value : DbFixtures.sha256();
        long size = column.equals("size_bytes") ? (Long) value : 1024L;
        String status = column.equals("status") ? (String) value : "RECEIVED";
        String objectKey = column.equals("object_key") ? (String) value : "obj/" + id;
        UUID uploader = column.equals("uploaded_by") ? (UUID) value : user;
        jdbcTemplate.update("""
                insert into documents(id, organization_id, claim_id, document_type, file_name, content_type,
                                      size_bytes, sha256, object_key, status, uploaded_by)
                values (?, ?, ?, 'FINAL_INVOICE', 'f.pdf', 'application/pdf', ?, ?, ?, ?, ?)
                """, id, organization, claim, size, sha, objectKey, status, uploader);
    }

    @Test
    void documentMustBelongToAClaimOfTheSameOrganization() {
        UUID otherOrganization = fixtures.organization();
        UUID otherUser = fixtures.user(otherOrganization);

        assertThatThrownBy(() -> fixtures.document(otherOrganization, claim, otherUser, DocumentType.FINAL_INVOICE, DbFixtures.sha256()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_documents_claim");
    }

    @Test
    void documentMustReferenceAnExistingClaim() {
        assertThatThrownBy(() -> document(UUID.randomUUID(), DocumentType.FINAL_INVOICE, DbFixtures.sha256()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_documents_claim");
    }

    @Test
    void sameFileCannotBeStoredTwiceForTheSameClaimAndType() {
        String sha = DbFixtures.sha256();
        document(claim, DocumentType.FINAL_INVOICE, sha);

        assertThatThrownBy(() -> document(claim, DocumentType.FINAL_INVOICE, sha))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_documents_active_checksum");
    }

    @Test
    void sameFileIsAllowedForADifferentTypeOrADifferentClaim() {
        String sha = DbFixtures.sha256();
        document(claim, DocumentType.FINAL_INVOICE, sha);

        assertThatCode(() -> document(claim, DocumentType.GARAGE_ESTIMATE, sha)).doesNotThrowAnyException();
        UUID otherClaim = fixtures.claim(organization, "CLM-2");
        assertThatCode(() -> document(otherClaim, DocumentType.FINAL_INVOICE, sha)).doesNotThrowAnyException();
    }

    @Test
    void aSupersededFileFreesItsChecksumForReupload() {
        String sha = DbFixtures.sha256();
        UUID first = document(claim, DocumentType.FINAL_INVOICE, sha);
        jdbcTemplate.update("update documents set superseded = true where id = ?", first);

        assertThatCode(() -> document(claim, DocumentType.FINAL_INVOICE, sha)).doesNotThrowAnyException();
    }

    @Test
    void aRevisionMustMatchTheClaimAndTypeOfTheFileItReplaces() {
        UUID original = document(claim, DocumentType.FINAL_INVOICE, DbFixtures.sha256());
        UUID otherClaim = fixtures.claim(organization, "CLM-2");

        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into documents(id, organization_id, claim_id, document_type, file_name, content_type, size_bytes,
                                      sha256, object_key, uploaded_by, supersedes_document_id)
                values (?, ?, ?, 'FINAL_INVOICE', 'f.pdf', 'application/pdf', 10, ?, ?, ?, ?)
                """, UUID.randomUUID(), organization, otherClaim, DbFixtures.sha256(), "obj/" + UUID.randomUUID(), user, original))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_documents_supersedes");

        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into documents(id, organization_id, claim_id, document_type, file_name, content_type, size_bytes,
                                      sha256, object_key, uploaded_by, supersedes_document_id)
                values (?, ?, ?, 'GARAGE_ESTIMATE', 'f.pdf', 'application/pdf', 10, ?, ?, ?, ?)
                """, UUID.randomUUID(), organization, claim, DbFixtures.sha256(), "obj/" + UUID.randomUUID(), user, original))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_documents_supersedes");
    }

    @Test
    void aDocumentCanBeReplacedOnlyOnce() {
        UUID original = document(claim, DocumentType.FINAL_INVOICE, DbFixtures.sha256());
        String insert = """
                insert into documents(id, organization_id, claim_id, document_type, file_name, content_type, size_bytes,
                                      sha256, object_key, uploaded_by, supersedes_document_id)
                values (?, ?, ?, 'FINAL_INVOICE', 'f.pdf', 'application/pdf', 10, ?, ?, ?, ?)
                """;
        jdbcTemplate.update(insert, UUID.randomUUID(), organization, claim, DbFixtures.sha256(), "obj/" + UUID.randomUUID(), user, original);

        assertThatThrownBy(() -> jdbcTemplate.update(insert, UUID.randomUUID(), organization, claim, DbFixtures.sha256(), "obj/" + UUID.randomUUID(), user, original))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_documents_superseded_once");
    }

    @Test
    void aDocumentCannotSupersedeItself() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into documents(id, organization_id, claim_id, document_type, file_name, content_type, size_bytes,
                                      sha256, object_key, uploaded_by, supersedes_document_id)
                values (?, ?, ?, 'FINAL_INVOICE', 'f.pdf', 'application/pdf', 10, ?, ?, ?, ?)
                """, id, organization, claim, DbFixtures.sha256(), "obj/" + id, user, id))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_documents_not_self_superseding");
    }

    @Test
    void checksumMustBeLowercaseHexOfTheRightLength() {
        assertThatThrownBy(() -> insertRaw("sha256", "A".repeat(64)))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_documents_sha256");
        assertThatThrownBy(() -> insertRaw("sha256", "abc123"))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_documents_sha256");
    }

    @Test
    void emptyFilesAreRejected() {
        assertThatThrownBy(() -> insertRaw("size_bytes", 0L))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_documents_size_positive");
    }

    @Test
    void statusMustBeAKnownValue() {
        assertThatThrownBy(() -> insertRaw("status", "NOT_RECEIVED"))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_documents_status");
    }

    @Test
    void objectKeyMustBeUnique() {
        String key = "obj/" + UUID.randomUUID();
        insertRaw("object_key", key);

        assertThatThrownBy(() -> insertRaw("object_key", key))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("uq_documents_object_key");
    }

    @Test
    void uploaderMustBeARealUser() {
        assertThatThrownBy(() -> insertRaw("uploaded_by", UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("fk_documents_uploaded_by");
    }

    @Test
    void entityMappingRoundTripsAndStartsAtReceived() {
        Document document = new Document();
        document.setOrganizationId(organization);
        document.setClaimId(claim);
        document.setDocumentType(DocumentType.GARAGE_ESTIMATE);
        document.setFileName("estimate.pdf");
        document.setContentType("application/pdf");
        document.setSizeBytes(2048);
        document.setSha256(DbFixtures.sha256());
        document.setObjectKey("obj/" + UUID.randomUUID());
        document.setUploadedBy(user);
        Instant before = Instant.now();
        Document saved = documentRepository.saveAndFlush(document);

        Document loaded = documentRepository.findByIdAndOrganizationId(saved.getId(), organization).orElseThrow();
        assertThat(loaded.getStatus()).isEqualTo(DocumentStatus.RECEIVED);
        assertThat(loaded.getDocumentType()).isEqualTo(DocumentType.GARAGE_ESTIMATE);
        assertThat(loaded.isSuperseded()).isFalse();
        assertThat(loaded.getReceivedAt()).isBetween(before.minusSeconds(5), Instant.now().plusSeconds(5));
        assertThat(loaded.getVersion()).isZero();
    }

    @Test
    void statusChangesAreVersionedForOptimisticLocking() {
        UUID id = document(claim, DocumentType.FINAL_INVOICE, DbFixtures.sha256());

        Document first = documentRepository.findById(id).orElseThrow();
        Document second = documentRepository.findById(id).orElseThrow();
        first.setStatus(DocumentStatus.PROCESSING);
        documentRepository.saveAndFlush(first);

        second.setStatus(DocumentStatus.FAILED);
        assertThatThrownBy(() -> documentRepository.saveAndFlush(second))
                .isInstanceOf(org.springframework.dao.OptimisticLockingFailureException.class);
        assertThat(documentRepository.findById(id).orElseThrow().getStatus()).isEqualTo(DocumentStatus.PROCESSING);
    }

    @Test
    void lookupThroughAnotherOrganizationFindsNothing() {
        UUID id = document(claim, DocumentType.FINAL_INVOICE, DbFixtures.sha256());
        UUID otherOrganization = fixtures.organization();

        assertThat(documentRepository.findByIdAndOrganizationId(id, otherOrganization)).isEmpty();
        assertThat(documentRepository.findByIdAndOrganizationId(id, organization)).isPresent();
    }
}
