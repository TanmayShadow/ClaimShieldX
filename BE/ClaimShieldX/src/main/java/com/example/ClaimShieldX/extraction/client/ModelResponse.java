package com.example.ClaimShieldX.extraction.client;

/** The model's answer as text (JSON), plus how many tokens the call used. Not yet parsed or trusted. */
public record ModelResponse(String json, Integer inputTokens, Integer outputTokens) {
}
