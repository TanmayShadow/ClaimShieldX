package com.example.ClaimShieldX.extraction.schema;

public record ModelTotals(
        ModelField subtotal,
        ModelField taxTotal,
        ModelField discountTotal,
        ModelField grandTotal
) {
}
