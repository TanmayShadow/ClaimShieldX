package com.example.ClaimShieldX.extraction.dto;

import com.example.ClaimShieldX.documents.model.DocumentStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

/**
 * The answer to a request to extract. The first implementation finishes before it answers, so {@code documentStatus} is
 * already final; once extraction moves into the background job it will be PROCESSING and clients poll the extraction.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExtractionStartedResponse(UUID documentId, DocumentStatus documentStatus, UUID extractionId, Integer attempt) {
}
