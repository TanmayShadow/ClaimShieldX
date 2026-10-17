package com.example.ClaimShieldX.extraction.client;

import com.example.ClaimShieldX.extraction.schema.ModelDocument;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

/**
 * Reads the model's answer into the typed schema, strictly: an unknown property, a wrong type, a trailing token or an
 * oversized document is rejected rather than tolerated. A rejected answer is treated like any other unusable output.
 */
public final class ModelOutputParser {
    public static class MalformedOutputException extends Exception {
        MalformedOutputException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static final JsonMapper MAPPER = JsonMapper.builder(JsonFactory.builder()
                    .streamReadConstraints(StreamReadConstraints.builder().maxStringLength(200_000).maxNestingDepth(12).maxDocumentLength(2_000_000L).build())
                    .build())
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build();

    private ModelOutputParser() {
    }

    public static ModelDocument parse(String json) throws MalformedOutputException {
        if (json == null || json.isBlank()) {
            throw new MalformedOutputException("The model returned no text", null);
        }
        try {
            ModelDocument document = MAPPER.readValue(json, ModelDocument.class);
            if (document == null) {
                throw new MalformedOutputException("The model returned an empty document", null);
            }
            return document;
        } catch (MalformedOutputException e) {
            throw e;
        } catch (Exception e) {
            // The message of a parsing error can quote the offending text, so only its type is kept.
            throw new MalformedOutputException("The model output did not match the schema (" + e.getClass().getSimpleName() + ")", e);
        }
    }
}
