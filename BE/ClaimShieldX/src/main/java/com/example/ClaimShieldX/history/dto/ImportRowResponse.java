package com.example.ClaimShieldX.history.dto;

import com.example.ClaimShieldX.history.model.HistoricalImportRow;
import com.example.ClaimShieldX.history.model.RowError;
import com.example.ClaimShieldX.history.model.RowStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/** One row's result. {@code rowNumber} counts data rows from 1 (the header is not a row). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ImportRowResponse(int rowNumber, RowStatus status, String sourceRecordId, List<RowError> errors) {
    public static ImportRowResponse of(HistoricalImportRow row) {
        return new ImportRowResponse(row.getRowNumber(), row.getStatus(), row.getSourceRecordId(), row.getErrors());
    }
}
