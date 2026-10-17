package com.example.ClaimShieldX.exceptions;

import com.example.ClaimShieldX.documents.model.DocumentType;

/** The document is of a kind the extraction does not read yet. */
public class ExtractionNotSupportedException extends RuntimeException {
    public ExtractionNotSupportedException(DocumentType type) {
        super("Extraction is not supported for " + type);
    }
}
