package com.example.ClaimShieldX.rules.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.math.BigDecimal;
import java.time.ZoneId;

/**
 * The tolerances and thresholds of the deterministic rules. They are insurer policy, not code: the values here are
 * documented defaults for the insurer to confirm, every run stores the values it used, and changing one changes the
 * configuration hash so an old run can always be explained.
 */
@ConfigurationProperties("app.rules")
public record RuleProperties(
        /** Zone that turns the claim's instants into calendar dates, and the dates printed on documents into comparable days. */
        @DefaultValue("Asia/Kolkata") ZoneId timeZone,
        /** The most a printed subtotal or grand total may differ from its recalculation because of a "round off" line. */
        @DefaultValue("1.00") BigDecimal roundOffAllowance,
        /** Added to the rounding error a unit price printed to the paisa can cause (0.005 per unit) when checking one line. */
        @DefaultValue("0.01") BigDecimal lineBaseAllowance,
        /** An invoice above the baseline by MORE than this percentage (and by the minimum amount) is flagged. */
        @DefaultValue("10.00") BigDecimal variancePercentThreshold,
        /** A small bill is not flagged for a large percentage: the increase must also reach this amount. */
        @DefaultValue("1000.00") BigDecimal varianceMinimumIncrease,
        /** Calendar days two sources may differ by before the dates are called inconsistent. */
        @DefaultValue("0") int dateToleranceDays,
        /** A one-day gap this close to midnight (in the zone) may be a time-zone effect, so it is not treated as material. */
        @DefaultValue("180") int nearMidnightMinutes,
        /** Share of description words two line items must have in common (0 to 1) to be called near-identical. */
        @DefaultValue("0.80") BigDecimal nearDuplicateSimilarity,
        /** A description with fewer words than this is too generic to compare ("Labour"). */
        @DefaultValue("2") int minDescriptionWords,
        /** An earlier claim on the same vehicle within this many days is the strongest kind of historical relationship. */
        @DefaultValue("365") int historyRecentDays,
        /** Most history matches listed inside a rule result (the count is always complete). */
        @DefaultValue("10") int maxListedMatches
) {
    public RuleProperties {
        require(roundOffAllowance.signum() >= 0, "app.rules.round-off-allowance must not be negative");
        require(lineBaseAllowance.signum() >= 0, "app.rules.line-base-allowance must not be negative");
        require(variancePercentThreshold.signum() >= 0, "app.rules.variance-percent-threshold must not be negative");
        require(varianceMinimumIncrease.signum() >= 0, "app.rules.variance-minimum-increase must not be negative");
        require(dateToleranceDays >= 0, "app.rules.date-tolerance-days must not be negative");
        require(nearMidnightMinutes >= 0 && nearMidnightMinutes <= 720, "app.rules.near-midnight-minutes must be between 0 and 720");
        require(nearDuplicateSimilarity.signum() > 0 && nearDuplicateSimilarity.compareTo(BigDecimal.ONE) <= 0,
                "app.rules.near-duplicate-similarity must be above 0 and at most 1");
        require(minDescriptionWords >= 1, "app.rules.min-description-words must be at least 1");
        require(historyRecentDays >= 0, "app.rules.history-recent-days must not be negative");
        require(maxListedMatches >= 1, "app.rules.max-listed-matches must be at least 1");
    }

    public static RuleProperties defaults() {
        return new RuleProperties(ZoneId.of("Asia/Kolkata"), new BigDecimal("1.00"), new BigDecimal("0.01"), new BigDecimal("10.00"),
                new BigDecimal("1000.00"), 0, 180, new BigDecimal("0.80"), 2, 365, 10);
    }

    /** Every value, as text, in a fixed order: stored with each run and hashed, so a run can be explained after the configuration changes. */
    public java.util.Map<String, Object> snapshot() {
        java.util.Map<String, Object> map = new java.util.TreeMap<>();
        map.put("timeZone", timeZone.getId());
        map.put("roundOffAllowance", roundOffAllowance.toPlainString());
        map.put("lineBaseAllowance", lineBaseAllowance.toPlainString());
        map.put("variancePercentThreshold", variancePercentThreshold.toPlainString());
        map.put("varianceMinimumIncrease", varianceMinimumIncrease.toPlainString());
        map.put("dateToleranceDays", dateToleranceDays);
        map.put("nearMidnightMinutes", nearMidnightMinutes);
        map.put("nearDuplicateSimilarity", nearDuplicateSimilarity.toPlainString());
        map.put("minDescriptionWords", minDescriptionWords);
        map.put("historyRecentDays", historyRecentDays);
        map.put("maxListedMatches", maxListedMatches);
        return map;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
