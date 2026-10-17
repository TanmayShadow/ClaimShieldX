package com.example.ClaimShieldX.history.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/** One problem with one row. {@code field} is a template column name, or null when the problem is the row as a whole. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RowError(String field, RowErrorCode code, String message) {
}
