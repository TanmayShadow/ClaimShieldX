package com.example.ClaimShieldX.documents.service;

import com.example.ClaimShieldX.auth.model.UserPrincipal;
import com.example.ClaimShieldX.claims.repository.ClaimsRepository;
import com.example.ClaimShieldX.common.PagedResponse;
import com.example.ClaimShieldX.common.utils.ConstraintViolations;
import com.example.ClaimShieldX.documents.dto.DocumentResponse;
import com.example.ClaimShieldX.documents.model.Document;
import com.example.ClaimShieldX.documents.model.DocumentType;
import com.example.ClaimShieldX.documents.repository.DocumentRepository;
import com.example.ClaimShieldX.documents.storage.DocumentStorage;
import com.example.ClaimShieldX.exceptions.*;
import com.example.ClaimShieldX.idempotency.service.IdempotencyService;
import com.example.ClaimShieldX.investigation.service.EvidenceWaitingNotifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/**
 * Document upload and retrieval. The order inside {@link #upload} matters: cheap checks first, the idempotency
 * marker before any side effect, the object in storage before the database row, and a compensating delete if
 * the row cannot be written, so a failure never leaves a row that points at a missing file.
 */
@Slf4j
@Service
public class DocumentService {
    public static final String UPLOAD_OPERATION = "POST /claims/{claimId}/documents";
    private static final String RESOURCE_TYPE = "DOCUMENT";
    private static final int MAX_EXTERNAL_ID_LENGTH = 255;

    public record UploadResult(DocumentResponse document, boolean replayed) {
    }

    public record DocumentContent(DocumentResponse metadata, InputStream stream) {
    }

    private final ClaimsRepository claimsRepository;
    private final DocumentRepository documentRepository;
    private final DocumentPersistence persistence;
    private final DocumentStorage storage;
    private final IdempotencyService idempotencyService;
    private final int maxDocumentsPerClaim;
    private final EvidenceWaitingNotifier waitingNotifier;

    public DocumentService(ClaimsRepository claimsRepository, DocumentRepository documentRepository,
                           DocumentPersistence persistence, DocumentStorage storage, IdempotencyService idempotencyService,
                           EvidenceWaitingNotifier waitingNotifier,
                           @Value("${app.documents.max-per-claim:50}") int maxDocumentsPerClaim) {
        this.claimsRepository = claimsRepository;
        this.documentRepository = documentRepository;
        this.persistence = persistence;
        this.storage = storage;
        this.idempotencyService = idempotencyService;
        this.waitingNotifier = waitingNotifier;
        this.maxDocumentsPerClaim = maxDocumentsPerClaim;
    }

    public UploadResult upload(UserPrincipal principal, UUID claimId, MultipartFile file, DocumentType documentType,
                               String externalDocumentId, UUID supersedesDocumentId, String idempotencyKey) {
        IdempotencyService.validateKey(idempotencyKey);
        String externalId = normalizeExternalId(externalDocumentId);
        UUID organizationId = principal.getOrganization().getId();
        requireClaim(organizationId, claimId);

        FileInspector.Inspection inspection = FileInspector.inspect(file);
        String fingerprint = IdempotencyService.fingerprint(claimId.toString(), documentType.name(),
                externalId == null ? "" : externalId, supersedesDocumentId == null ? "" : supersedesDocumentId.toString(), inspection.sha256());

        IdempotencyService.Begin begin = idempotencyService.begin(organizationId, UPLOAD_OPERATION, idempotencyKey, fingerprint);
        if (begin.mode() == IdempotencyService.Mode.REPLAY) {
            Document original = documentRepository.findByIdAndOrganizationIdAndClaimId(begin.resourceId(), organizationId, claimId)
                    .orElseThrow(() -> new IllegalStateException("A completed idempotency key points at a missing document"));
            return new UploadResult(toResponse(original), true);
        }

        String objectKey = null;
        try {
            checkLimit(claimId);
            checkNotDuplicate(claimId, documentType, inspection.sha256());
            checkRevisionTarget(organizationId, claimId, documentType, supersedesDocumentId);

            objectKey = "documents/" + organizationId + "/" + claimId + "/" + UUID.randomUUID();
            try (InputStream content = file.getInputStream()) {
                storage.put(objectKey, content, inspection.sizeBytes(), inspection.type().contentType());
            } catch (IOException e) {
                throw RequestValidationException.of("file", "could not be read");
            }

            Document saved = persistRow(principal, organizationId, claimId, file, documentType, externalId, supersedesDocumentId, objectKey, inspection);
            idempotencyService.complete(begin.keyId(), 201, RESOURCE_TYPE, saved.getId());
            log.info("Document stored: organization={} claim={} document={} type={} sizeBytes={}",
                    organizationId, claimId, saved.getId(), documentType, inspection.sizeBytes());
            waitingNotifier.documentAdded(organizationId, claimId);
            return new UploadResult(toResponse(saved), false);
        } catch (RuntimeException e) {
            deleteQuietly(objectKey);
            idempotencyService.release(begin.keyId());
            throw e;
        }
    }

    public PagedResponse<DocumentResponse> list(UserPrincipal principal, UUID claimId, Pageable pageable) {
        UUID organizationId = principal.getOrganization().getId();
        requireClaim(organizationId, claimId);
        return PagedResponse.of(documentRepository.findByOrganizationIdAndClaimId(organizationId, claimId, pageable), DocumentService::toResponse);
    }

    public DocumentResponse get(UserPrincipal principal, UUID claimId, UUID documentId) {
        return toResponse(find(principal.getOrganization().getId(), claimId, documentId));
    }

    /** The caller must close the stream. No database transaction is held while the file streams out. */
    public DocumentContent openContent(UserPrincipal principal, UUID claimId, UUID documentId) {
        Document document = find(principal.getOrganization().getId(), claimId, documentId);
        try {
            return new DocumentContent(toResponse(document), storage.open(document.getObjectKey()));
        } catch (StorageObjectNotFoundException e) {
            log.error("Document {} exists in the database but its stored object is missing (key {})", document.getId(), document.getObjectKey());
            throw e;
        }
    }

    private Document find(UUID organizationId, UUID claimId, UUID documentId) {
        return documentRepository.findByIdAndOrganizationIdAndClaimId(documentId, organizationId, claimId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));
    }

    private void requireClaim(UUID organizationId, UUID claimId) {
        if (!claimsRepository.existsByIdAndOrganizationId(claimId, organizationId)) {
            throw new ResourceNotFoundException("Claim not found");
        }
    }

    private String normalizeExternalId(String externalDocumentId) {
        if (externalDocumentId == null || externalDocumentId.isBlank()) {
            return null;
        }
        String trimmed = externalDocumentId.strip();
        if (trimmed.length() > MAX_EXTERNAL_ID_LENGTH) {
            throw RequestValidationException.of("externalDocumentId", "cannot exceed " + MAX_EXTERNAL_ID_LENGTH + " characters");
        }
        return trimmed;
    }

    private void checkLimit(UUID claimId) {
        if (documentRepository.countByClaimId(claimId) >= maxDocumentsPerClaim) {
            throw new DocumentLimitReachedException();
        }
    }

    private void checkNotDuplicate(UUID claimId, DocumentType type, String sha256) {
        documentRepository.findByClaimIdAndDocumentTypeAndSha256AndSupersededFalse(claimId, type, sha256).ifPresent(existing -> {
            throw new DuplicateDocumentException(existing.getId());
        });
    }

    // A foreign or unknown id gets the same generic message, so the response reveals nothing about other organizations.
    private void checkRevisionTarget(UUID organizationId, UUID claimId, DocumentType type, UUID supersedesDocumentId) {
        if (supersedesDocumentId == null) {
            return;
        }
        Document target = documentRepository.findByIdAndOrganizationIdAndClaimId(supersedesDocumentId, organizationId, claimId)
                .orElseThrow(() -> RequestValidationException.of("supersedesDocumentId", "document not found"));
        if (target.getDocumentType() != type) {
            throw RequestValidationException.of("supersedesDocumentId", "must have the same document type");
        }
        if (target.isSuperseded()) {
            throw new InvalidStateTransitionException("The document was already replaced");
        }
    }

    private Document persistRow(UserPrincipal principal, UUID organizationId, UUID claimId, MultipartFile file, DocumentType type,
                                String externalId, UUID supersedesDocumentId, String objectKey, FileInspector.Inspection inspection) {
        Document document = new Document();
        document.setOrganizationId(organizationId);
        document.setClaimId(claimId);
        document.setDocumentType(type);
        document.setFileName(FileNameSanitizer.sanitize(file.getOriginalFilename(), inspection.type()));
        document.setContentType(inspection.type().contentType());
        document.setSizeBytes(inspection.sizeBytes());
        document.setSha256(inspection.sha256());
        document.setObjectKey(objectKey);
        document.setExternalDocumentId(externalId);
        document.setSupersedesDocumentId(supersedesDocumentId);
        document.setUploadedBy(principal.getId());
        try {
            return persistence.persist(document, supersedesDocumentId);
        } catch (DataIntegrityViolationException e) {
            // Two uploads of the same file raced past the earlier check; the database constraint decides.
            if (ConstraintViolations.isViolationOf(e, "uq_documents_active_checksum")) {
                UUID existing = documentRepository.findByClaimIdAndDocumentTypeAndSha256AndSupersededFalse(claimId, type, inspection.sha256())
                        .map(Document::getId).orElse(null);
                if (existing != null) {
                    throw new DuplicateDocumentException(existing);
                }
            }
            if (ConstraintViolations.isViolationOf(e, "uq_documents_superseded_once")) {
                throw new InvalidStateTransitionException("The document was already replaced");
            }
            throw e;
        }
    }

    // If even the cleanup fails the object is orphaned; its key is logged so it can be reconciled (it holds no claim data in the key).
    private void deleteQuietly(String objectKey) {
        if (objectKey == null) {
            return;
        }
        try {
            storage.delete(objectKey);
        } catch (RuntimeException e) {
            log.error("Could not remove the stored object {} after a failed upload; it is orphaned and needs cleanup", objectKey, e);
        }
    }

    static DocumentResponse toResponse(Document document) {
        return new DocumentResponse(document.getId(), document.getClaimId(), document.getDocumentType(), document.getFileName(),
                document.getContentType(), document.getSizeBytes(), document.getSha256(), document.getStatus(),
                document.getExternalDocumentId(), document.getSupersedesDocumentId(), document.isSuperseded(), document.getReceivedAt());
    }
}
