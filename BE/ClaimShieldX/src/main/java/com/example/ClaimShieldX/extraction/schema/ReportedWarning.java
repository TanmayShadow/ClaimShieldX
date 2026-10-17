package com.example.ClaimShieldX.extraction.schema;

import com.fasterxml.jackson.annotation.JsonInclude;

/** A problem with the document that the model itself reported (illegible area, overwriting, instruction-like text). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReportedWarning(String code, Integer page, String quote) {
}
