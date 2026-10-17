package com.example.ClaimShieldX.extraction.dto;

import com.example.ClaimShieldX.documents.model.DocumentStatus;
import com.example.ClaimShieldX.documents.model.DocumentType;
import com.example.ClaimShieldX.extraction.model.ExtractionFailureCode;
import com.example.ClaimShieldX.extraction.model.ExtractionRunStatus;
import com.example.ClaimShieldX.extraction.schema.*;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The latest extraction of a document. {@code status} is the attempt's outcome; {@code documentStatus} is where the
 * document stands now (VERIFIED, NEEDS_REVIEW or FAILED). VERIFIED means the extraction was schema-valid and its identifiers
 * and dates agreed with the claim record, not that the document's content is true. For a failed attempt only the failure
 * fields are present. Never contains a storage key, a checksum or a file name.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExtractionResponse(
        UUID documentId,
        DocumentType documentType,
        UUID extractionId,
        int attempt,
        ExtractionRunStatus status,
        DocumentStatus documentStatus,
        ExtractionFailureCode failureCode,
        Boolean retryable,
        String schemaVersion,
        String promptVersion,
        String modelId,
        Integer pageCount,
        Instant createdAt,
        List<FieldResult> fields,
        List<LineItemResult> lineItems,
        ComputedTotals computed,
        List<ReportedWarning> modelWarnings,
        List<ReviewIssue> issues
) {
}
