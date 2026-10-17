package com.example.ClaimShieldX.extraction.schema;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

/**
 * Where in the document a value was read. {@code quote} is the model's reading of the text near the value; it is shown
 * to a human reviewer and is not checked against the file by the application.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Source(UUID documentId, Integer page, String quote) {
}
