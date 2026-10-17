package com.example.ClaimShieldX.history.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.time.ZoneId;

/** Limits and conventions for importing historical data and matching claims against it. */
@ConfigurationProperties("app.history")
public record HistoryProperties(
        /** Largest CSV file accepted (5 MiB). A larger file is not read at all: the import fails with FILE_TOO_LARGE. */
        @DefaultValue("5242880") long maxFileBytes,
        @DefaultValue("10000") int maxRows,
        /** A date without a time ("2025-03-14") means the start of that day in this zone, and is recorded as date-only. */
        @DefaultValue("Asia/Kolkata") ZoneId dateOnlyZone,
        /** Most prior claims returned by one history lookup (the rest are counted, not listed). */
        @DefaultValue("50") int maxMatches,
        /** Most rows read from the database for one kind of match, before they are grouped into prior claims. */
        @DefaultValue("500") int maxCandidates,
        /** An import still PROCESSING after this long belonged to a process that died; the next import of its kind marks it failed. */
        @DefaultValue("15m") Duration staleProcessingAfter
) {
    public HistoryProperties {
        if (maxFileBytes < 1 || maxRows < 1 || maxMatches < 1 || maxCandidates < 1 || staleProcessingAfter.isNegative() || staleProcessingAfter.isZero()) {
            throw new IllegalArgumentException("app.history limits must be positive");
        }
    }

    public static HistoryProperties defaults() {
        return new HistoryProperties(5_242_880L, 10_000, ZoneId.of("Asia/Kolkata"), 50, 500, Duration.ofMinutes(15));
    }
}
