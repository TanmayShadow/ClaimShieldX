package com.example.ClaimShieldX.history.csv;

import com.example.ClaimShieldX.history.model.RowError;

import java.util.List;

/**
 * The outcome of validating one row: the data when every check passed, otherwise the errors. {@code sourceRecordId} is the
 * row's record id whenever it was readable, even for a rejected row, so the row can be found again.
 */
public record Validated<T>(T data, List<RowError> errors, String sourceRecordId) {
    public boolean accepted() {
        return errors.isEmpty();
    }
}
