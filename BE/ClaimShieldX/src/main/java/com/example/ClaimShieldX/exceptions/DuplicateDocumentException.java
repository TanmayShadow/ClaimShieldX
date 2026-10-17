package com.example.ClaimShieldX.exceptions;

import java.util.UUID;

public class DuplicateDocumentException extends RuntimeException {
    private final UUID existingDocumentId;

    public DuplicateDocumentException(UUID existingDocumentId) {
        super("The same file is already stored for this claim and document type");
        this.existingDocumentId = existingDocumentId;
    }

    public UUID getExistingDocumentId() {
        return existingDocumentId;
    }
}
