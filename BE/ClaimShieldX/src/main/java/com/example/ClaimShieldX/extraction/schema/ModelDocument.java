package com.example.ClaimShieldX.extraction.schema;

import java.util.List;

/**
 * The whole answer of the model for one document (schema version itemized-cost-2). It is parsed strictly: an unknown
 * property or a wrong type rejects the answer. Nothing in it is trusted until Java has normalized and checked it.
 */
public record ModelDocument(
        ModelHeader header,
        List<ModelLineItem> lineItems,
        ModelTotals totals,
        List<ModelWarning> warnings
) {
}
