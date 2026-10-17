package com.example.ClaimShieldX.extraction.schema;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One extracted value. {@code value} is the text as printed in the document and is never changed. {@code normalizedValue}
 * is what Java made of it (a plain decimal, an ISO date, a currency code); it is null when the text could not be
 * normalized, and {@code normalizationNote} says how it was read when that involved an assumption.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FieldResult(
        String name,
        FieldState state,
        String value,
        String normalizedValue,
        String normalizationNote,
        String unknownReason,
        Source source
) {
}
