package com.example.ClaimShieldX.extraction.client;

/**
 * The narrow door to a model provider: one call, one document, one JSON answer. Retries, fallback to another model, rate
 * limiting and validation all live outside it, so they work the same for every provider and are tested without one.
 */
public interface ExtractionModelClient {
    ModelResponse extract(ModelRequest request) throws ModelCallException;
}
