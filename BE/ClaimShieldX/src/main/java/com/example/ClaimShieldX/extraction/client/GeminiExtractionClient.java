package com.example.ClaimShieldX.extraction.client;

import com.example.ClaimShieldX.extraction.client.ModelCallException.Category;
import com.example.ClaimShieldX.extraction.config.ExtractionProperties;
import com.google.genai.Client;
import com.google.genai.errors.ApiException;
import com.google.genai.types.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Calls Gemini through the Google GenAI SDK with the project's API key (Developer API, not Vertex AI). One call per
 * invocation: the SDK's own retries are switched off so every retry is counted and bounded in {@link ModelCaller}.
 *
 * <p>The document is sent inline. On the free tier Google may let people review prompts, so only synthetic documents may be
 * extracted while this provider is configured with a free-tier key.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.extraction.provider", havingValue = "gemini", matchIfMissing = true)
public class GeminiExtractionClient implements ExtractionModelClient {
    private static final Pattern RETRY_DELAY = Pattern.compile("(?i)retry(?:Delay\"?\\s*:\\s*\"?|\\s+in\\s+)(\\d+(?:\\.\\d+)?)\\s*s");

    private final Client client;
    private final ExtractionProperties properties;

    public GeminiExtractionClient(ExtractionProperties properties, @Value("${spring.ai.google.genai.api-key:}") String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("app.extraction.provider=gemini needs an API key: set SPRING_AI_GOOGLE_GENAI_API_KEY "
                    + "(or spring.ai.google.genai.api-key), or set app.extraction.provider=fake");
        }
        this.properties = properties;
        HttpOptions http = HttpOptions.builder()
                .timeout((int) properties.requestTimeout().toMillis())
                .retryOptions(HttpRetryOptions.builder().attempts(1).build())
                .build();
        this.client = Client.builder().apiKey(apiKey).httpOptions(http).build();
        log.warn("Documents will be sent to the Gemini API (free-tier terms allow human review of prompts): use synthetic data only");
    }

    @Override
    public ModelResponse extract(ModelRequest request) throws ModelCallException {
        GenerateContentResponse response;
        try {
            Content content = Content.fromParts(Part.fromBytes(request.content(), request.mimeType()),
                    Part.fromText(ExtractionPrompt.user(request.documentType())));
            GenerateContentConfig config = GenerateContentConfig.builder()
                    .systemInstruction(Content.fromParts(Part.fromText(ExtractionPrompt.SYSTEM)))
                    .temperature(0f)
                    .candidateCount(1)
                    .maxOutputTokens(properties.maxOutputTokens())
                    .responseMimeType("application/json")
                    .responseJsonSchema(ExtractionSchemas.modelOutput())
                    .thinkingConfig(ThinkingConfig.builder().thinkingLevel(thinkingLevel()).build())
                    .build();
            response = client.models.generateContent(request.modelId(), content, config);
        } catch (RuntimeException e) {
            throw classify(e);
        }
        return toModelResponse(response);
    }

    private ThinkingLevel.Known thinkingLevel() {
        return properties.thinkingLevel().equals("MINIMAL") ? ThinkingLevel.Known.MINIMAL : ThinkingLevel.Known.LOW;
    }

    public static ModelResponse toModelResponse(GenerateContentResponse response) throws ModelCallException {
        if (response.promptFeedback().flatMap(GenerateContentResponsePromptFeedback::blockReason).isPresent()) {
            throw new ModelCallException(Category.BLOCKED, "The prompt was blocked by the model service", null);
        }
        Candidate candidate = response.candidates().flatMap(list -> list.stream().findFirst()).orElse(null);
        if (candidate == null) {
            throw new ModelCallException(Category.TRANSIENT, "The model returned no candidate", null);
        }
        FinishReason.Known finish = candidate.finishReason().map(FinishReason::knownEnum).orElse(FinishReason.Known.FINISH_REASON_UNSPECIFIED);
        switch (finish) {
            case MAX_TOKENS -> throw new ModelCallException(Category.TRUNCATED_OUTPUT, "The model output was cut off at the token limit", null);
            case SAFETY, RECITATION, BLOCKLIST, PROHIBITED_CONTENT, SPII, IMAGE_SAFETY, IMAGE_PROHIBITED_CONTENT, IMAGE_RECITATION ->
                    throw new ModelCallException(Category.BLOCKED, "The model declined to read the document (" + finish + ")", null);
            case STOP, FINISH_REASON_UNSPECIFIED -> {
            }
            default -> throw new ModelCallException(Category.TRANSIENT, "The model stopped early (" + finish + ")", null);
        }
        String text = response.text();
        if (text == null || text.isBlank()) {
            throw new ModelCallException(Category.TRANSIENT, "The model returned no text", null);
        }
        Integer input = response.usageMetadata().flatMap(GenerateContentResponseUsageMetadata::promptTokenCount).orElse(null);
        Integer output = response.usageMetadata().map(u -> u.candidatesTokenCount().orElse(0) + u.thoughtsTokenCount().orElse(0)).orElse(null);
        return new ModelResponse(text, input, output);
    }

    /** Sorts an SDK failure into what should happen next. Only the status code and status text are ever logged. */
    public static ModelCallException classify(RuntimeException e) {
        if (e instanceof ApiException api) {
            int code = api.code();
            String message = api.message() == null ? "" : api.message();
            log.warn("Gemini API error: HTTP {} {}", code, api.status());
            if (code == 429) {
                // The free tier names the exhausted limit in the message, for example "...PerDayPerProjectPerModel-FreeTier".
                if (message.contains("PerDay")) {
                    return new ModelCallException(Category.QUOTA_EXHAUSTED, "Daily quota exhausted", e);
                }
                return new ModelCallException(Category.RATE_LIMITED, "Rate limited", e, retryDelay(message));
            }
            if (code == 400 && message.toLowerCase().contains("api key")) {
                return new ModelCallException(Category.NOT_CONFIGURED, "The API key was rejected", e);
            }
            if (code == 401 || code == 403 || code == 404) {
                return new ModelCallException(Category.NOT_CONFIGURED, "The model service refused the request (HTTP " + code + ")", e);
            }
            if (code == 408 || code >= 500) {
                return new ModelCallException(Category.TRANSIENT, "The model service failed (HTTP " + code + ")", e);
            }
            return new ModelCallException(Category.REJECTED_INPUT, "The model service rejected the request (HTTP " + code + ")", e);
        }
        if (isNetworkProblem(e)) {
            return new ModelCallException(Category.TRANSIENT, "Network problem calling the model service", e);
        }
        return new ModelCallException(Category.TRANSIENT, "Unexpected error calling the model service (" + e.getClass().getSimpleName() + ")", e);
    }

    private static boolean isNetworkProblem(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof IOException || t instanceof SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }

    static Duration retryDelay(String message) {
        Matcher matcher = RETRY_DELAY.matcher(message);
        if (!matcher.find()) {
            return null;
        }
        return Duration.ofMillis((long) (Double.parseDouble(matcher.group(1)) * 1000));
    }
}
