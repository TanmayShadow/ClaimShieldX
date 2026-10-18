package com.example.ClaimShieldX.analysis.reasoning;

import com.example.ClaimShieldX.extraction.client.GeminiExtractionClient;
import com.example.ClaimShieldX.extraction.client.ModelCallException;
import com.example.ClaimShieldX.extraction.client.ModelResponse;
import com.example.ClaimShieldX.extraction.config.ExtractionProperties;
import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import com.google.genai.types.Part;
import com.google.genai.types.ThinkingConfig;
import com.google.genai.types.ThinkingLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Calls Gemini with the evidence package as text. Uses the same key, timeouts and error sorting as document reading; the SDK's own
 * retries are off so the caller counts every request. Only the evidence package is sent: no document file, no claim notes.
 */
@Component
@ConditionalOnProperty(name = "app.extraction.provider", havingValue = "gemini", matchIfMissing = true)
public class GeminiReasoningClient implements ReasoningModelClient {
    private final Client client;
    private final ExtractionProperties properties;

    public GeminiReasoningClient(ExtractionProperties properties, @Value("${spring.ai.google.genai.api-key:}") String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("app.extraction.provider=gemini needs an API key: set SPRING_AI_GOOGLE_GENAI_API_KEY");
        }
        this.properties = properties;
        HttpOptions http = HttpOptions.builder().timeout((int) properties.requestTimeout().toMillis())
                .retryOptions(HttpRetryOptions.builder().attempts(1).build()).build();
        this.client = Client.builder().apiKey(apiKey).httpOptions(http).build();
    }

    @Override
    public ModelResponse generate(Request request) throws ModelCallException {
        GenerateContentResponse response;
        try {
            GenerateContentConfig config = GenerateContentConfig.builder()
                    .systemInstruction(Content.fromParts(Part.fromText(request.system())))
                    .temperature(0f)
                    .candidateCount(1)
                    .maxOutputTokens(properties.maxOutputTokens())
                    .responseMimeType("application/json")
                    .responseJsonSchema(request.schema())
                    .thinkingConfig(ThinkingConfig.builder().thinkingLevel(properties.thinkingLevel().equals("MINIMAL") ? ThinkingLevel.Known.MINIMAL : ThinkingLevel.Known.LOW).build())
                    .build();
            response = client.models.generateContent(request.modelId(), Content.fromParts(Part.fromText(request.user())), config);
        } catch (RuntimeException e) {
            throw GeminiExtractionClient.classify(e);
        }
        return GeminiExtractionClient.toModelResponse(response);
    }
}
