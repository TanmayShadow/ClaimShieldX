package com.example.ClaimShieldX.documents.dto;

import com.example.ClaimShieldX.documents.model.DocumentStatus;
import com.example.ClaimShieldX.documents.model.DocumentType;

import java.time.Instant;
import java.util.UUID;

// Deliberately has no object key, storage location, organization or uploader.
public record DocumentResponse(
        UUID id,
        UUID claimId,
        DocumentType documentType,
        String fileName,
        String contentType,
        long sizeBytes,
        String sha256,
        DocumentStatus status,
        String externalDocumentId,
        UUID supersedesDocumentId,
        boolean superseded,
        Instant receivedAt
) {
}
