package com.example.ClaimShieldX.history.model;

/** Why a whole file could not be imported. No row of such a file is stored. */
public enum ImportFailureCode {
    EMPTY_FILE,
    FILE_TOO_LARGE,
    TOO_MANY_ROWS,
    INVALID_ENCODING,
    INVALID_CSV,
    INVALID_HEADER,
    INTERNAL_ERROR
}
