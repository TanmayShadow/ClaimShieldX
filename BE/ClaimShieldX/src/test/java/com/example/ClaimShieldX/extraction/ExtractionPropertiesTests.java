package com.example.ClaimShieldX.extraction;

import com.example.ClaimShieldX.extraction.client.ModelRequest;
import com.example.ClaimShieldX.extraction.config.ExtractionProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExtractionPropertiesTests {
    private static ExtractionProperties with(String provider, String model, List<String> fallbacks, String thinking, int tokens, int attempts) {
        return new ExtractionProperties(provider, model, fallbacks, "s", "1", thinking, tokens, Duration.ofSeconds(60), attempts, Duration.ofSeconds(1),
                Duration.ofSeconds(10), 10, 2, 1000L, 40, 200, 5, Duration.ofMinutes(5), new BigDecimal("1.00"), ZoneId.of("UTC"));
    }

    @Test
    void theDefaultsMatchTheMeasuredFreeTierPlan() {
        ExtractionProperties p = ExtractionProperties.defaults();

        assertThat(p.model()).isEqualTo("gemini-3.5-flash-lite");
        assertThat(p.fallbackModels()).containsExactly("gemini-3.1-flash-lite");
        assertThat(p.maxRequestsPerMinute()).isLessThan(15); // the project's free-tier limit
        assertThat(p.maxInlineBytes()).isEqualTo(20L * 1024 * 1024); // the upload limit: every stored original may be read
        assertThat(p.deadline()).isEqualTo(Duration.ofSeconds(180));
        assertThat(p.processingTimeout()).isGreaterThan(p.deadline().plus(p.requestTimeout())); // a slow run is never taken over while it is going
        assertThat(p.thinkingLevel()).isEqualTo("LOW");
        assertThat(p.modelChain()).containsExactly("gemini-3.5-flash-lite", "gemini-3.1-flash-lite");
    }

    @Test
    void theModelChainHasNoRepeatsAndIgnoresBlankEntries() {
        ExtractionProperties p = with("fake", "a", List.of("a", "", "b", "b", " "), "LOW", 8192, 3);

        assertThat(p.modelChain()).containsExactly("a", "b");
    }

    @Test
    void valuesAreNormalisedLikeUpperAndLowerCase() {
        ExtractionProperties p = with("FAKE", "m", List.of(), "minimal", 8192, 3);

        assertThat(p.provider()).isEqualTo("fake");
        assertThat(p.thinkingLevel()).isEqualTo("MINIMAL");
    }

    @Test
    void badSettingsStopTheApplicationFromStartingWithAClearMessage() {
        assertThatThrownBy(() -> with("openai", "m", List.of(), "LOW", 8192, 3)).hasMessageContaining("provider");
        assertThatThrownBy(() -> with("fake", " ", List.of(), "LOW", 8192, 3)).hasMessageContaining("model");
        assertThatThrownBy(() -> with("fake", "m", List.of(), "HIGH", 8192, 3)).hasMessageContaining("thinking-level");
        assertThatThrownBy(() -> with("fake", "m", List.of(), "LOW", 100, 3)).hasMessageContaining("max-output-tokens");
        assertThatThrownBy(() -> with("fake", "m", List.of(), "LOW", 8192, 0)).hasMessageContaining("max-model-attempts");
        assertThatThrownBy(() -> with("fake", "m", List.of(), "LOW", 8192, 7)).hasMessageContaining("max-model-attempts");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rateAndSizeLimitsMustBePositive(int bad) {
        assertThatThrownBy(() -> new ExtractionProperties("fake", "m", List.of(), "s", "1", "LOW", 8192, Duration.ofSeconds(60), 3, Duration.ofSeconds(1),
                Duration.ofSeconds(10), bad, 2, 1000L, 40, 200, 5, Duration.ofMinutes(5), new BigDecimal("1.00"), ZoneId.of("UTC"))).hasMessageContaining("requests-per-minute");
        assertThatThrownBy(() -> new ExtractionProperties("fake", "m", List.of(), "s", "1", "LOW", 8192, Duration.ofSeconds(60), 3, Duration.ofSeconds(1),
                Duration.ofSeconds(10), 10, 2, bad, 40, 200, 5, Duration.ofMinutes(5), new BigDecimal("1.00"), ZoneId.of("UTC"))).hasMessageContaining("max-inline-bytes");
    }

    @Test
    void theModelRequestCarriesOnlyTheDocumentAndTheSetupNeverClaimData() {
        // Adding a field here (a claim id, a token, an amount) must be a deliberate decision: the claim record never goes to the model.
        assertThat(Arrays.stream(ModelRequest.class.getRecordComponents()).map(RecordComponent::getName))
                .containsExactly("documentType", "content", "mimeType", "modelId", "schemaVersion", "promptVersion");
    }
}
