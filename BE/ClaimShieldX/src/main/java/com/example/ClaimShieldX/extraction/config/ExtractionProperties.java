package com.example.ClaimShieldX.extraction.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Everything that decides how documents are read by the model, in one place so nothing about the model, the
 * prompt or the limits is hidden in code. The model id, schema version and prompt version are stored with every
 * extraction, so an old result can always be traced to the exact setup that produced it.
 *
 * <p>The defaults match the measured free-tier limits of the project key (15 requests a minute, 500 a day on
 * gemini-3.5-flash-lite); the request rate is kept below the per-minute limit on purpose.
 */
@ConfigurationProperties("app.extraction")
public record ExtractionProperties(
        /** "gemini" calls the model; "fake" is for tests and never leaves the machine. */
        @DefaultValue("gemini") String provider,
        @DefaultValue("gemini-3.5-flash-lite") String model,
        /** Tried in order only when the primary model is out of quota or unavailable after its retries. */
        @DefaultValue("gemini-3.1-flash-lite") List<String> fallbackModels,
        @DefaultValue("itemized-cost-2") String schemaVersion,
        @DefaultValue("1") String promptVersion,
        /** MINIMAL or LOW. Hidden reasoning tokens count against the output limit, so this stays small. */
        @DefaultValue("LOW") String thinkingLevel,
        @DefaultValue("16384") int maxOutputTokens,
        @DefaultValue("90s") Duration requestTimeout,
        /** Model calls per model, including the first. Only transient failures and unusable output are retried. */
        @DefaultValue("3") int maxModelAttempts,
        @DefaultValue("2s") Duration retryInitialDelay,
        @DefaultValue("30s") Duration retryMaxDelay,
        @DefaultValue("10") int maxRequestsPerMinute,
        @DefaultValue("2") int maxConcurrentRequests,
        /** Largest original sent to the model inline. Larger files are not sent (see the extraction docs). */
        @DefaultValue("20971520") long maxInlineBytes,
        @DefaultValue("40") int maxPages,
        @DefaultValue("200") int maxLineItems,
        /** Extractions started by hand for one document, over its whole life. */
        @DefaultValue("5") int maxAttemptsPerDocument,
        /**
         * A document still PROCESSING after this long is treated as abandoned and may be extracted again. It must stay longer
         * than {@link #deadline()} plus one request timeout, or a slow run could be taken over while it is still going.
         */
        @DefaultValue("5m") Duration processingTimeout,
        /** Sanity threshold for the recalculated totals. Not the materiality policy of the later rules. */
        @DefaultValue("1.00") BigDecimal arithmeticTolerance,
        /** Zone used to turn the claim's incident instant into a calendar date for comparison with document dates. */
        @DefaultValue("Asia/Kolkata") ZoneId documentTimeZone
) {
    private static final Set<String> PROVIDERS = Set.of("gemini", "fake");
    private static final Set<String> THINKING_LEVELS = Set.of("MINIMAL", "LOW");

    public ExtractionProperties {
        provider = provider.toLowerCase(Locale.ROOT);
        thinkingLevel = thinkingLevel.toUpperCase(Locale.ROOT);
        fallbackModels = fallbackModels == null ? List.of() : fallbackModels.stream().filter(m -> !m.isBlank()).toList();
        require(PROVIDERS.contains(provider), "app.extraction.provider must be 'gemini' or 'fake'");
        require(!model.isBlank(), "app.extraction.model must not be blank");
        require(THINKING_LEVELS.contains(thinkingLevel), "app.extraction.thinking-level must be MINIMAL or LOW");
        require(maxOutputTokens >= 1024, "app.extraction.max-output-tokens must be at least 1024");
        require(maxModelAttempts >= 1 && maxModelAttempts <= 6, "app.extraction.max-model-attempts must be between 1 and 6");
        require(maxRequestsPerMinute >= 1, "app.extraction.max-requests-per-minute must be at least 1");
        require(maxConcurrentRequests >= 1, "app.extraction.max-concurrent-requests must be at least 1");
        require(maxInlineBytes > 0, "app.extraction.max-inline-bytes must be positive");
        require(maxPages >= 1 && maxLineItems >= 1 && maxAttemptsPerDocument >= 1, "app.extraction limits must be positive");
        require(!requestTimeout.isNegative() && !requestTimeout.isZero(), "app.extraction.request-timeout must be positive");
        require(arithmeticTolerance.signum() >= 0, "app.extraction.arithmetic-tolerance must not be negative");
    }

    /** All defaults, for tests and for code that needs the standard limits. */
    public static ExtractionProperties defaults() {
        return new ExtractionProperties("gemini", "gemini-3.5-flash-lite", List.of("gemini-3.1-flash-lite"), "itemized-cost-2", "1",
                "LOW", 16384, Duration.ofSeconds(90), 3, Duration.ofSeconds(2), Duration.ofSeconds(30), 10, 2, 20_971_520L, 40, 200, 5,
                Duration.ofMinutes(5), new BigDecimal("1.00"), ZoneId.of("Asia/Kolkata"));
    }

    /**
     * The longest all model calls of one extraction may take together: no new call starts after it, and no wait would run
     * past it. One call already in flight can still run for up to the request timeout beyond it.
     */
    public Duration deadline() {
        return requestTimeout.multipliedBy(2);
    }

    /** The primary model followed by the fallbacks, without repeats. */
    public List<String> modelChain() {
        return java.util.stream.Stream.concat(java.util.stream.Stream.of(model), fallbackModels.stream()).distinct().toList();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
