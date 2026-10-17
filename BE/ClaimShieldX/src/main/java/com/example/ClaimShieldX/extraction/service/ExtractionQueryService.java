package com.example.ClaimShieldX.extraction.service;

import com.example.ClaimShieldX.auth.model.UserPrincipal;
import com.example.ClaimShieldX.claims.repository.ClaimsRepository;
import com.example.ClaimShieldX.documents.model.Document;
import com.example.ClaimShieldX.documents.repository.DocumentRepository;
import com.example.ClaimShieldX.exceptions.ResourceNotFoundException;
import com.example.ClaimShieldX.extraction.dto.ExtractionResponse;
import com.example.ClaimShieldX.extraction.model.DocumentExtraction;
import com.example.ClaimShieldX.extraction.model.ExtractionRunStatus;
import com.example.ClaimShieldX.extraction.repository.DocumentExtractionRepository;
import com.example.ClaimShieldX.extraction.schema.ExtractionResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Reads the latest extraction of a document, always through its claim and organization. */
@Service
public class ExtractionQueryService {
    private final ClaimsRepository claimsRepository;
    private final DocumentRepository documentRepository;
    private final DocumentExtractionRepository extractionRepository;

    public ExtractionQueryService(ClaimsRepository claimsRepository, DocumentRepository documentRepository,
                                  DocumentExtractionRepository extractionRepository) {
        this.claimsRepository = claimsRepository;
        this.documentRepository = documentRepository;
        this.extractionRepository = extractionRepository;
    }

    @Transactional(readOnly = true)
    public ExtractionResponse latest(UserPrincipal principal, UUID claimId, UUID documentId) {
        UUID organizationId = principal.getOrganization().getId();
        if (!claimsRepository.existsByIdAndOrganizationId(claimId, organizationId)) {
            throw new ResourceNotFoundException("Claim not found");
        }
        Document document = documentRepository.findByIdAndOrganizationIdAndClaimId(documentId, organizationId, claimId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));
        DocumentExtraction extraction = extractionRepository.findFirstByOrganizationIdAndDocumentIdOrderByAttemptDesc(organizationId, documentId)
                .orElseThrow(() -> new ResourceNotFoundException("This document has not been extracted yet"));

        boolean failed = extraction.getStatus() == ExtractionRunStatus.FAILED;
        ExtractionResult result = extraction.getResult();
        return new ExtractionResponse(documentId, document.getDocumentType(), extraction.getId(), extraction.getAttempt(),
                extraction.getStatus(), document.getStatus(), extraction.getFailureCode(),
                failed ? extraction.getFailureCode().retryable() : null,
                extraction.getSchemaVersion(), extraction.getPromptVersion(), extraction.getModelId(), extraction.getPageCount(),
                extraction.getCreatedAt(),
                failed ? null : result.fields(), failed ? null : result.lineItems(), failed ? null : result.computed(),
                failed ? null : result.modelWarnings(), failed ? null : result.issues());
    }
}
