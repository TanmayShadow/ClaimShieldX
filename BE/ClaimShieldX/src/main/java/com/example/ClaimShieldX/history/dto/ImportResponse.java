package com.example.ClaimShieldX.history.dto;

import com.example.ClaimShieldX.history.model.HistoricalImport;
import com.example.ClaimShieldX.history.model.ImportFailureCode;
import com.example.ClaimShieldX.history.model.ImportStatus;
import com.example.ClaimShieldX.history.model.ImportType;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/** An import and how it went. Never contains text from the uploaded file, only counts and safe codes. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ImportResponse(
        UUID id,
        ImportType importType,
        String templateVersion,
        String source,
        ImportStatus status,
        ImportFailureCode failureCode,
        String failureDetail,
        String sha256,
        long sizeBytes,
        ImportCounts counts,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt
) {
    public static ImportResponse of(HistoricalImport i) {
        return new ImportResponse(i.getId(), i.getImportType(), i.getTemplateVersion(), i.getSource(), i.getStatus(), i.getFailureCode(),
                i.getFailureDetail(), i.getSha256(), i.getSizeBytes(),
                new ImportCounts(i.getTotalRows(), i.getAcceptedRows(), i.getRejectedRows(), i.getSkippedDuplicateRows()),
                i.getCreatedAt(), i.getStartedAt(), i.getFinishedAt());
    }
}
