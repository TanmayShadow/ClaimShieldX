package com.example.ClaimShieldX.extraction.client;

import com.example.ClaimShieldX.documents.model.DocumentType;

/**
 * What one model call receives: the original document and which kind it is. The claim record is deliberately absent, so
 * the model can never echo a claim's identifiers back and defeat the cross-check.
 */
public record ModelRequest(DocumentType documentType, byte[] content, String mimeType, String modelId,
                           String schemaVersion, String promptVersion) {
}
