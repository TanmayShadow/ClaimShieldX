package com.example.ClaimShieldX.extraction.schema;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Totals recalculated by Java from the extracted line items. The model is never the arithmetic authority. A difference
 * is a reason for a person to look, not a finding: tax, discount, rounding or a misread digit can all explain it.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ComputedTotals(
        String lineTotalSum,
        String expectedGrandTotal,
        String grandTotalDifference,
        String tolerance
) {
}
