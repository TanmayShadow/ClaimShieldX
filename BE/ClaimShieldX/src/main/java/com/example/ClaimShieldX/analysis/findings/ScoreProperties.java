package com.example.ClaimShieldX.analysis.findings;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The review-priority score is transparent and configurable: a fixed number of points per rule result, more when the deviation is
 * material (REVIEW) than when it is only noted (INFO). The defaults are for the insurer to confirm. They are not probabilities.
 */
@ConfigurationProperties("app.scoring")
public record ScoreProperties(
        @DefaultValue("1") String version,
        Map<String, Integer> reviewPoints,
        Map<String, Integer> infoPoints,
        @DefaultValue("20") int mediumFrom,
        @DefaultValue("50") int highFrom
) {
    static final Map<String, Integer> REVIEW = Map.of("DOCUMENT_FILE_DUPLICATE", 30, "INVOICE_IDENTITY_DUPLICATE", 30, "ESTIMATE_INVOICE_VARIANCE", 25,
            "HISTORICAL_RELATIONSHIP", 20, "INVOICE_ARITHMETIC", 15, "DATE_CONSISTENCY", 15, "REPEATED_LINE_ITEMS", 10);
    static final Map<String, Integer> INFO = Map.of("DOCUMENT_FILE_DUPLICATE", 5, "INVOICE_IDENTITY_DUPLICATE", 5, "ESTIMATE_INVOICE_VARIANCE", 5,
            "HISTORICAL_RELATIONSHIP", 3, "INVOICE_ARITHMETIC", 3, "DATE_CONSISTENCY", 3, "REPEATED_LINE_ITEMS", 2);

    public ScoreProperties {
        Map<String, Integer> review = new LinkedHashMap<>(REVIEW);
        if (reviewPoints != null) {
            review.putAll(reviewPoints);
        }
        Map<String, Integer> info = new LinkedHashMap<>(INFO);
        if (infoPoints != null) {
            info.putAll(infoPoints);
        }
        reviewPoints = Map.copyOf(review);
        infoPoints = Map.copyOf(info);
        if (reviewPoints.values().stream().anyMatch(v -> v < 0 || v > 100) || infoPoints.values().stream().anyMatch(v -> v < 0 || v > 100)) {
            throw new IllegalArgumentException("app.scoring points must be between 0 and 100");
        }
        if (mediumFrom < 1 || highFrom <= mediumFrom || highFrom > 100) {
            throw new IllegalArgumentException("app.scoring needs 1 <= medium-from < high-from <= 100");
        }
    }

    public static ScoreProperties defaults() {
        return new ScoreProperties("1", null, null, 20, 50);
    }
}
