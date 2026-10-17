package com.example.ClaimShieldX.extraction.schema;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * The validated extraction as stored and returned: header and total fields, line items, the totals Java recalculated,
 * what the model reported about the document, and the review issues. Unknown values are explicit, never guessed.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExtractionResult(
        List<FieldResult> fields,
        List<LineItemResult> lineItems,
        ComputedTotals computed,
        List<ReportedWarning> modelWarnings,
        List<ReviewIssue> issues
) {
}
