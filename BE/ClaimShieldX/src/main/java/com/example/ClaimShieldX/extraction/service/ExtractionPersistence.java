package com.example.ClaimShieldX.extraction.service;

import com.example.ClaimShieldX.documents.model.Document;
import com.example.ClaimShieldX.documents.model.DocumentStatus;
import com.example.ClaimShieldX.documents.repository.DocumentRepository;
import com.example.ClaimShieldX.exceptions.InvalidStateTransitionException;
import com.example.ClaimShieldX.extraction.config.ExtractionProperties;
import com.example.ClaimShieldX.extraction.model.DocumentExtraction;
import com.example.ClaimShieldX.extraction.model.ExtractionFailureCode;
import com.example.ClaimShieldX.extraction.model.ExtractionRunStatus;
import com.example.ClaimShieldX.extraction.repository.DocumentExtractionRepository;
import com.example.ClaimShieldX.extraction.schema.ExtractionResult;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The short database transactions around an extraction. The model call itself runs in none of them, so no connection is
 * held while waiting for the model. Each method changes the document's status and the attempt record together.
 */
@Component
class ExtractionPersistence {
    private static final List<DocumentStatus> STARTABLE = List.of(DocumentStatus.RECEIVED, DocumentStatus.NEEDS_REVIEW, DocumentStatus.FAILED);

    /** What an attempt cost and which model it ended with. */
    record Spent(String modelId, int modelCalls, Integer inputTokens, Integer outputTokens, Integer pageCount, Duration duration) {
    }

    private final DocumentRepository documentRepository;
    private final DocumentExtractionRepository extractionRepository;
    private final ExtractionProperties properties;

    ExtractionPersistence(DocumentRepository documentRepository, DocumentExtractionRepository extractionRepository, ExtractionProperties properties) {
        this.documentRepository = documentRepository;
        this.extractionRepository = extractionRepository;
        this.properties = properties;
    }

    /** Claims the document for extraction. False when someone else holds it or it may not be extracted. */
    @Transactional
    boolean begin(UUID documentId, UUID organizationId) {
        Instant now = Instant.now();
        return documentRepository.markProcessing(documentId, organizationId, DocumentStatus.PROCESSING, STARTABLE,
                now, now.minus(properties.processingTimeout())) == 1;
    }

    @Transactional
    DocumentExtraction succeed(Document document, ExtractionResult result, DocumentStatus finalStatus, Spent spent) {
        DocumentExtraction row = newRow(document, spent);
        row.setStatus(ExtractionRunStatus.SUCCEEDED);
        row.setResult(result);
        return save(row, document.getId(), finalStatus);
    }

    @Transactional
    DocumentExtraction fail(Document document, ExtractionFailureCode code, Spent spent) {
        DocumentExtraction row = newRow(document, spent);
        row.setStatus(ExtractionRunStatus.FAILED);
        row.setFailureCode(code);
        return save(row, document.getId(), DocumentStatus.FAILED);
    }

    private DocumentExtraction newRow(Document document, Spent spent) {
        DocumentExtraction row = new DocumentExtraction();
        row.setOrganizationId(document.getOrganizationId());
        row.setClaimId(document.getClaimId());
        row.setDocumentId(document.getId());
        row.setDocumentType(document.getDocumentType());
        row.setAttempt(extractionRepository.lastAttempt(document.getId()) + 1);
        row.setSchemaVersion(properties.schemaVersion());
        row.setPromptVersion(properties.promptVersion());
        row.setModelId(spent.modelId());
        row.setPageCount(spent.pageCount());
        row.setModelCalls(spent.modelCalls());
        row.setInputTokens(spent.inputTokens());
        row.setOutputTokens(spent.outputTokens());
        row.setDurationMs(spent.duration().toMillis());
        return row;
    }

    private DocumentExtraction save(DocumentExtraction row, UUID documentId, DocumentStatus finalStatus) {
        DocumentExtraction saved = extractionRepository.saveAndFlush(row);
        // Zero rows means the document was no longer ours (taken over after a timeout); the whole transaction is undone.
        if (documentRepository.transition(documentId, DocumentStatus.PROCESSING, finalStatus, Instant.now()) != 1) {
            throw new InvalidStateTransitionException("The document is no longer being processed by this request");
        }
        return saved;
    }
}
