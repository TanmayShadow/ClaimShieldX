package com.example.ClaimShieldX.analysis.reasoning;

import com.example.ClaimShieldX.extraction.client.ModelCallException;
import com.example.ClaimShieldX.extraction.client.ModelResponse;

import java.util.Map;

/** The narrow door to the language model for reasoning: one text prompt in, one JSON answer out. Retries and validation live outside it. */
public interface ReasoningModelClient {
    record Request(String role, String modelId, String system, String user, Map<String, Object> schema) {
    }

    ModelResponse generate(Request request) throws ModelCallException;
}
