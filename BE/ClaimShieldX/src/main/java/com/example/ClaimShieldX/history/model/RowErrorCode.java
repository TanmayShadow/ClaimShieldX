package com.example.ClaimShieldX.history.model;

/** Why one row was rejected. The message never repeats the value that was wrong. */
public enum RowErrorCode {
    WRONG_COLUMN_COUNT,
    MISSING_REQUIRED,
    TOO_LONG,
    INVALID_TOKEN,
    INVALID_NUMBER,
    INVALID_DATE,
    INVALID_VALUE,
    DATE_ORDER,
    COUNT_EXCEEDS_CLAIM_COUNT,
    CONFLICTING_DUPLICATE,
    DUPLICATE_CLAIM_VERSION,
    OVERLAPPING_PERIOD
}
