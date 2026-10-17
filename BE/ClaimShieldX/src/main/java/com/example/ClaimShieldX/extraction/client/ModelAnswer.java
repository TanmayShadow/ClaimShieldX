package com.example.ClaimShieldX.extraction.client;

import com.example.ClaimShieldX.extraction.schema.ModelDocument;

/** A parsed answer, the model that gave it, and what getting it cost. */
public record ModelAnswer(ModelDocument document, String modelId, int modelCalls, Integer inputTokens, Integer outputTokens) {
}
