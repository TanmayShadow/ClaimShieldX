package com.example.ClaimShieldX.extraction.schema;

/**
 * One value as the model reported it, exactly as printed in the document. {@code value} is null when the model could not
 * read or find it, and then {@code unknownReason} says why. The model is never asked to calculate or normalize.
 */
public record ModelField(String value, Integer page, String quote, String unknownReason) {
}
