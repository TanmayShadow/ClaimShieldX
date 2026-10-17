package com.example.ClaimShieldX.extraction.schema;

/**
 * One row of the items table, as printed. The whole row shares one page and one quote, and every cell is plain text (null
 * when the row has no such cell). A row carries one source instead of one per cell: that keeps the answer short enough for
 * invoices with many items, which would otherwise run out of output tokens or time.
 */
public record ModelLineItem(
        Integer page,
        String quote,
        String description,
        String category,
        String quantity,
        String unitPrice,
        String tax,
        String lineTotal
) {
}
