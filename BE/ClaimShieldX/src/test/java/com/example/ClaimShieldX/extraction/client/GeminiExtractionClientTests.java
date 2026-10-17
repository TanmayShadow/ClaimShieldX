package com.example.ClaimShieldX.extraction.client;

import com.example.ClaimShieldX.extraction.client.ModelCallException.Category;
import com.example.ClaimShieldX.extraction.config.ExtractionProperties;
import com.google.genai.errors.ApiException;
import com.google.genai.errors.GenAiIOException;
import com.google.genai.types.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** How the adapter sorts the Gemini service's answers and failures. No network is used. */
class GeminiExtractionClientTests {
    private static ModelCallException classify(int code, String status, String message) {
        return GeminiExtractionClient.classify(new ApiException(code, status, message));
    }

    // ------------------------------------------------------------ failures

    @Test
    void aDailyQuotaIsNotRetriedOnTheSameModel() {
        ModelCallException e = classify(429, "RESOURCE_EXHAUSTED",
                "Quota exceeded for metric generate_content_free_tier_requests, limit: GenerateRequestsPerDayPerProjectPerModel-FreeTier");

        assertThat(e.category()).isEqualTo(Category.QUOTA_EXHAUSTED);
    }

    @Test
    void aPerMinuteLimitIsRetriedAfterTheDelayTheServiceGave() {
        ModelCallException e = classify(429, "RESOURCE_EXHAUSTED",
                "Quota exceeded for GenerateRequestsPerMinutePerProjectPerModel-FreeTier. Please retry in 23.5s.");

        assertThat(e.category()).isEqualTo(Category.RATE_LIMITED);
        assertThat(e.retryAfter()).isEqualTo(Duration.ofMillis(23_500));
    }

    @Test
    void theRetryDelayIsAlsoReadFromTheStructuredDetail() {
        assertThat(GeminiExtractionClient.retryDelay("... \"retryDelay\": \"30s\" ...")).isEqualTo(Duration.ofSeconds(30));
        assertThat(GeminiExtractionClient.retryDelay("... retryDelay\":\"2.5s")).isEqualTo(Duration.ofMillis(2500));
        assertThat(GeminiExtractionClient.retryDelay("no hint here")).isNull();
        assertThat(GeminiExtractionClient.retryDelay("")).isNull();
    }

    @Test
    void aRateLimitWithoutAHintHasNoRetryAfter() {
        ModelCallException e = classify(429, "RESOURCE_EXHAUSTED", "Too many requests");

        assertThat(e.category()).isEqualTo(Category.RATE_LIMITED);
        assertThat(e.retryAfter()).isNull();
    }

    @ParameterizedTest
    @CsvSource({"500,INTERNAL", "502,BAD_GATEWAY", "503,UNAVAILABLE", "504,DEADLINE_EXCEEDED", "408,REQUEST_TIMEOUT"})
    void serverSideFailuresAreTransient(int code, String status) {
        assertThat(classify(code, status, "service problem").category()).isEqualTo(Category.TRANSIENT);
    }

    @ParameterizedTest
    @CsvSource({"400,INVALID_ARGUMENT", "413,PAYLOAD_TOO_LARGE", "422,UNPROCESSABLE", "415,UNSUPPORTED"})
    void anInvalidRequestIsRejectedInputAndNeverRetried(int code, String status) {
        assertThat(classify(code, status, "The request is invalid").category()).isEqualTo(Category.REJECTED_INPUT);
    }

    @ParameterizedTest
    @CsvSource({"401,UNAUTHENTICATED", "403,PERMISSION_DENIED", "404,NOT_FOUND"})
    void aRefusedKeyOrAnUnknownModelIsAConfigurationProblem(int code, String status) {
        assertThat(classify(code, status, "no access").category()).isEqualTo(Category.NOT_CONFIGURED);
    }

    @Test
    void anInvalidApiKeyIsAConfigurationProblemEvenThoughItComesAs400() {
        assertThat(classify(400, "INVALID_ARGUMENT", "API key not valid. Please pass a valid API key.").category()).isEqualTo(Category.NOT_CONFIGURED);
    }

    @Test
    void aMissingMessageDoesNotBreakClassification() {
        assertThat(classify(429, "RESOURCE_EXHAUSTED", null).category()).isEqualTo(Category.RATE_LIMITED);
        assertThat(classify(400, "INVALID_ARGUMENT", null).category()).isEqualTo(Category.REJECTED_INPUT);
    }

    @Test
    void networkProblemsAreTransientHoweverDeeplyTheyAreWrapped() {
        assertThat(GeminiExtractionClient.classify(new GenAiIOException("io", new IOException("reset"))).category()).isEqualTo(Category.TRANSIENT);
        assertThat(GeminiExtractionClient.classify(new RuntimeException("wrapped", new RuntimeException(new SocketTimeoutException("slow")))).category())
                .isEqualTo(Category.TRANSIENT);
    }

    @Test
    void anUnexpectedErrorIsTransientButBounded() {
        assertThat(GeminiExtractionClient.classify(new IllegalStateException("sdk bug")).category()).isEqualTo(Category.TRANSIENT);
    }

    @Test
    void failureMessagesNeverContainTheServiceText() {
        ModelCallException e = classify(400, "INVALID_ARGUMENT", "The document says: SECRET-DOCUMENT-TEXT");

        assertThat(e.getMessage()).doesNotContain("SECRET-DOCUMENT-TEXT");
    }

    // ------------------------------------------------------------ answers

    private static Candidate candidate(String text, FinishReason.Known finish) {
        Candidate.Builder builder = Candidate.builder().finishReason(new FinishReason(finish));
        if (text != null) {
            builder.content(Content.fromParts(Part.fromText(text)));
        }
        return builder.build();
    }

    private static GenerateContentResponse response(Candidate candidate) {
        return GenerateContentResponse.builder().candidates(List.of(candidate))
                .usageMetadata(GenerateContentResponseUsageMetadata.builder().promptTokenCount(1500).candidatesTokenCount(400).thoughtsTokenCount(100).build()).build();
    }

    @Test
    void aFinishedAnswerGivesItsTextAndTokenCounts() throws Exception {
        ModelResponse answer = GeminiExtractionClient.toModelResponse(response(candidate("{\"a\":1}", FinishReason.Known.STOP)));

        assertThat(answer.json()).isEqualTo("{\"a\":1}");
        assertThat(answer.inputTokens()).isEqualTo(1500);
        assertThat(answer.outputTokens()).isEqualTo(500); // answer plus hidden reasoning tokens
    }

    @Test
    void anAnswerCutOffAtTheTokenLimitIsTruncatedOutput() {
        assertThatThrownBy(() -> GeminiExtractionClient.toModelResponse(response(candidate("{\"a\":", FinishReason.Known.MAX_TOKENS))))
                .isInstanceOfSatisfying(ModelCallException.class, e -> assertThat(e.category()).isEqualTo(Category.TRUNCATED_OUTPUT));
    }

    @ParameterizedTest
    @CsvSource({"SAFETY", "RECITATION", "BLOCKLIST", "PROHIBITED_CONTENT", "SPII", "IMAGE_SAFETY"})
    void aPolicyStopIsBlockedAndNotRetried(String reason) {
        assertThatThrownBy(() -> GeminiExtractionClient.toModelResponse(response(candidate(null, FinishReason.Known.valueOf(reason)))))
                .isInstanceOfSatisfying(ModelCallException.class, e -> assertThat(e.category()).isEqualTo(Category.BLOCKED));
    }

    @Test
    void aBlockedPromptIsBlocked() {
        GenerateContentResponse blocked = GenerateContentResponse.builder()
                .promptFeedback(GenerateContentResponsePromptFeedback.builder().blockReason(new BlockedReason(BlockedReason.Known.SAFETY)).build()).build();

        assertThatThrownBy(() -> GeminiExtractionClient.toModelResponse(blocked))
                .isInstanceOfSatisfying(ModelCallException.class, e -> assertThat(e.category()).isEqualTo(Category.BLOCKED));
    }

    @Test
    void anEmptyOrOddAnswerIsTransient() {
        assertThatThrownBy(() -> GeminiExtractionClient.toModelResponse(GenerateContentResponse.builder().build()))
                .isInstanceOfSatisfying(ModelCallException.class, e -> assertThat(e.category()).isEqualTo(Category.TRANSIENT));
        assertThatThrownBy(() -> GeminiExtractionClient.toModelResponse(response(candidate("   ", FinishReason.Known.STOP))))
                .isInstanceOfSatisfying(ModelCallException.class, e -> assertThat(e.category()).isEqualTo(Category.TRANSIENT));
        assertThatThrownBy(() -> GeminiExtractionClient.toModelResponse(response(candidate("{}", FinishReason.Known.OTHER))))
                .isInstanceOfSatisfying(ModelCallException.class, e -> assertThat(e.category()).isEqualTo(Category.TRANSIENT));
    }

    // ------------------------------------------------------------ construction

    @Test
    void withoutAnApiKeyItRefusesToStartAndSaysHowToFixIt() {
        assertThatThrownBy(() -> new GeminiExtractionClient(ExtractionProperties.defaults(), "  "))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("SPRING_AI_GOOGLE_GENAI_API_KEY").hasMessageContaining("fake");
        assertThatThrownBy(() -> new GeminiExtractionClient(ExtractionProperties.defaults(), null)).isInstanceOf(IllegalStateException.class);
    }
}
