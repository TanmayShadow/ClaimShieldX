package com.example.ClaimShieldX.extraction.service;

import com.example.ClaimShieldX.auth.model.UserPrincipal;
import com.example.ClaimShieldX.claims.model.Claim;
import com.example.ClaimShieldX.claims.repository.ClaimsRepository;
import com.example.ClaimShieldX.documents.model.Document;
import com.example.ClaimShieldX.documents.model.DocumentStatus;
import com.example.ClaimShieldX.documents.model.DocumentType;
import com.example.ClaimShieldX.documents.repository.DocumentRepository;
import com.example.ClaimShieldX.documents.storage.DocumentStorage;
import com.example.ClaimShieldX.exceptions.*;
import com.example.ClaimShieldX.extraction.client.ModelAnswer;
import com.example.ClaimShieldX.extraction.client.ModelCaller;
import com.example.ClaimShieldX.extraction.client.ModelFailure;
import com.example.ClaimShieldX.extraction.config.ExtractionProperties;
import com.example.ClaimShieldX.extraction.model.DocumentExtraction;
import com.example.ClaimShieldX.extraction.model.ExtractionFailureCode;
import com.example.ClaimShieldX.extraction.repository.DocumentExtractionRepository;
import com.example.ClaimShieldX.extraction.validate.ExtractionProcessor;
import com.example.ClaimShieldX.extraction.validate.ExtractionProcessor.ClaimFacts;
import com.example.ClaimShieldX.extraction.validate.ExtractionProcessor.Processed;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Reads one document with the model and records the outcome. The document is claimed first (a status change that cannot be
 * won twice), the model is called with no database transaction open, and the result is validated in Java before anything
 * is stored. Whatever happens after the claim, the document leaves PROCESSING: VERIFIED, NEEDS_REVIEW or FAILED.
 */
@Slf4j
@Service
public class ExtractionService {
    /** The document kinds that have an extraction schema. Both are itemized cost documents. */
    public static final Set<DocumentType> SUPPORTED = EnumSet.of(DocumentType.FINAL_INVOICE, DocumentType.GARAGE_ESTIMATE);

    private final ClaimsRepository claimsRepository;
    private final DocumentRepository documentRepository;
    private final DocumentExtractionRepository extractionRepository;
    private final DocumentStorage storage;
    private final ModelCaller modelCaller;
    private final ExtractionProcessor processor;
    private final ExtractionPersistence persistence;
    private final ExtractionProperties properties;

    public ExtractionService(ClaimsRepository claimsRepository, DocumentRepository documentRepository,
                             DocumentExtractionRepository extractionRepository, DocumentStorage storage, ModelCaller modelCaller,
                             ExtractionProcessor processor, ExtractionPersistence persistence, ExtractionProperties properties) {
        this.claimsRepository = claimsRepository;
        this.documentRepository = documentRepository;
        this.extractionRepository = extractionRepository;
        this.storage = storage;
        this.modelCaller = modelCaller;
        this.processor = processor;
        this.persistence = persistence;
        this.properties = properties;
    }

    public ExtractionOutcome extract(UserPrincipal principal, UUID claimId, UUID documentId) {
        return extract(principal.getOrganization().getId(), claimId, documentId);
    }

    /** The same, for a caller that has an organization but no signed-in user (the investigation worker). */
    public ExtractionOutcome extract(UUID organizationId, UUID claimId, UUID documentId) {
        Claim claim = claimsRepository.findByIdAndOrganizationId(claimId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Claim not found"));
        Document document = documentRepository.findByIdAndOrganizationIdAndClaimId(documentId, organizationId, claimId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));
        if (!SUPPORTED.contains(document.getDocumentType())) {
            throw new ExtractionNotSupportedException(document.getDocumentType());
        }
        if (extractionRepository.countByDocumentId(documentId) >= properties.maxAttemptsPerDocument()) {
            throw new InvalidStateTransitionException("The extraction attempt limit for this document was reached");
        }
        ClaimFacts facts = factsOf(claim);

        if (!persistence.begin(documentId, organizationId)) {
            DocumentStatus status = documentRepository.findByIdAndOrganizationId(documentId, organizationId)
                    .map(Document::getStatus).orElse(DocumentStatus.VERIFIED);
            if (status == DocumentStatus.PROCESSING) {
                throw new RequestInProgressException();
            }
            throw new InvalidStateTransitionException("The document cannot be extracted in its current state");
        }

        long started = System.nanoTime();
        try {
            return run(document, facts, started);
        } catch (RuntimeException e) {
            // Never leave the document PROCESSING: record the failure, best effort, and report the original problem.
            log.error("Extraction of document {} failed unexpectedly", documentId, e);
            try {
                return outcome(persistence.fail(document, ExtractionFailureCode.INTERNAL_ERROR,
                        spent(properties.model(), 0, null, null, null, started)), DocumentStatus.FAILED);
            } catch (RuntimeException secondary) {
                log.error("Could not record the failure of document {}; it will be extractable again after the processing timeout", documentId, secondary);
                throw e;
            }
        }
    }

    private ExtractionOutcome run(Document document, ClaimFacts facts, long started) {
        UUID documentId = document.getId();
        if (document.getSizeBytes() > properties.maxInlineBytes()) {
            return failed(document, ExtractionFailureCode.DOCUMENT_TOO_LARGE, properties.model(), 0, null, started);
        }
        byte[] content;
        try (InputStream in = storage.open(document.getObjectKey())) {
            content = in.readNBytes((int) document.getSizeBytes());
        } catch (StorageObjectNotFoundException | StorageUnavailableException | IOException e) {
            log.error("Could not read the stored original of document {}", documentId, e);
            return failed(document, ExtractionFailureCode.STORAGE_UNAVAILABLE, properties.model(), 0, null, started);
        }

        // Evidence integrity: only the exact file that was uploaded may be read. A changed or damaged object is never sent on.
        if (content.length != document.getSizeBytes() || !sha256(content).equals(document.getSha256())) {
            log.error("The stored original of document {} no longer matches its recorded size or checksum", documentId);
            return failed(document, ExtractionFailureCode.STORED_FILE_MISMATCH, properties.model(), 0, null, started);
        }

        int pages = 1;
        if (document.getContentType().equals("application/pdf")) {
            try {
                pages = PdfPages.count(content);
            } catch (PdfPages.UnreadablePdfException e) {
                return failed(document, ExtractionFailureCode.UNREADABLE_FILE, properties.model(), 0, null, started);
            }
            if (pages > properties.maxPages()) {
                return failed(document, ExtractionFailureCode.UNREADABLE_FILE, properties.model(), 0, pages, started);
            }
        }

        ModelAnswer answer;
        try {
            answer = modelCaller.call(document.getDocumentType(), content, document.getContentType());
        } catch (ModelFailure failure) {
            return failed(document, failure.code(), failure.modelId(), failure.modelCalls(), pages, started);
        }

        Processed processed = processor.process(answer.document(), document.getDocumentType(), documentId, pages, facts);
        DocumentExtraction saved = persistence.succeed(document, processed.result(), processed.status(),
                spent(answer.modelId(), answer.modelCalls(), answer.inputTokens(), answer.outputTokens(), pages, started));
        return outcome(saved, processed.status());
    }

    private ExtractionOutcome failed(Document document, ExtractionFailureCode code, String modelId, int calls, Integer pages, long started) {
        DocumentExtraction saved = persistence.fail(document, code, spent(modelId, calls, null, null, pages, started));
        return outcome(saved, DocumentStatus.FAILED);
    }

    private static ExtractionPersistence.Spent spent(String modelId, int calls, Integer in, Integer out, Integer pages, long startedNanos) {
        return new ExtractionPersistence.Spent(modelId, calls, in, out, pages, Duration.ofNanos(System.nanoTime() - startedNanos));
    }

    private static ExtractionOutcome outcome(DocumentExtraction saved, DocumentStatus status) {
        return new ExtractionOutcome(saved.getDocumentId(), status, saved.getId(), saved.getAttempt());
    }

    private static String sha256(byte[] content) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(content));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }

    private static ClaimFacts factsOf(Claim claim) {
        return new ClaimFacts(
                claim.getGarage() == null ? null : claim.getGarage().getGarageId(),
                claim.getVehicle() == null ? null : claim.getVehicle().getVehicleToken(),
                claim.getFinancial() == null ? null : claim.getFinancial().getCurrency(),
                claim.getIncident() == null ? null : claim.getIncident().getIncidentAt());
    }
}
