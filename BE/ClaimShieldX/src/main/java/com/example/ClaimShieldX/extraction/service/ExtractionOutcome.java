package com.example.ClaimShieldX.extraction.service;

import com.example.ClaimShieldX.documents.model.DocumentStatus;

import java.util.UUID;

/** What an extraction request ended with. {@code extractionId} and {@code attempt} are the stored attempt. */
public record ExtractionOutcome(UUID documentId, DocumentStatus documentStatus, UUID extractionId, int attempt) {
}
