package com.example.ClaimShieldX.rules.service;

import com.example.ClaimShieldX.rules.config.RuleProperties;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The fingerprint must be a function of the data alone: same data, same text, same hash; different data, different hash. */
class RuleJsonTests {
    record Sample(String b, BigDecimal amount, Instant at, Map<String, Object> map) {
    }

    @Test
    void equalDataGivesEqualTextAndHashRegardlessOfMapInsertionOrder() {
        Map<String, Object> one = new LinkedHashMap<>();
        one.put("z", 1);
        one.put("a", 2);
        Map<String, Object> two = new LinkedHashMap<>();
        two.put("a", 2);
        two.put("z", 1);

        assertThat(RuleJson.canonical(new Sample("x", new BigDecimal("100.00"), Instant.parse("2026-09-10T05:00:00Z"), one)))
                .isEqualTo(RuleJson.canonical(new Sample("x", new BigDecimal("100.00"), Instant.parse("2026-09-10T05:00:00Z"), two)));
    }

    @Test
    void decimalsAreWrittenPlainAndDatesAsText() {
        String text = RuleJson.canonical(new Sample("x", new BigDecimal("1E+3"), Instant.parse("2026-09-10T05:00:00Z"), Map.of()));

        assertThat(text).contains("\"amount\":1000").contains("\"at\":\"2026-09-10T05:00:00Z\"").doesNotContain("E+");
    }

    @Test
    void anyDifferenceChangesTheHash() {
        String base = RuleJson.canonical(new Sample("x", new BigDecimal("100.00"), Instant.parse("2026-09-10T05:00:00Z"), Map.of()));

        assertThat(RuleJson.sha256(base)).matches("[0-9a-f]{64}")
                .isNotEqualTo(RuleJson.sha256(RuleJson.canonical(new Sample("y", new BigDecimal("100.00"), Instant.parse("2026-09-10T05:00:00Z"), Map.of()))))
                .isNotEqualTo(RuleJson.sha256(RuleJson.canonical(new Sample("x", new BigDecimal("100.01"), Instant.parse("2026-09-10T05:00:00Z"), Map.of()))))
                .isNotEqualTo(RuleJson.sha256(RuleJson.canonical(new Sample("x", new BigDecimal("100.00"), Instant.parse("2026-09-10T05:00:01Z"), Map.of()))))
                .isEqualTo(RuleJson.sha256(base));
    }

    @Test
    void aChangedThresholdChangesTheConfigurationHash() {
        RuleProperties d = RuleProperties.defaults();
        RuleProperties stricter = new RuleProperties(d.timeZone(), d.roundOffAllowance(), d.lineBaseAllowance(), new BigDecimal("5.00"), d.varianceMinimumIncrease(),
                d.dateToleranceDays(), d.nearMidnightMinutes(), d.nearDuplicateSimilarity(), d.minDescriptionWords(), d.historyRecentDays(), d.maxListedMatches());

        String defaults = RuleJson.sha256(RuleJson.canonical(d.snapshot()));
        assertThat(RuleJson.sha256(RuleJson.canonical(stricter.snapshot()))).isNotEqualTo(defaults);
        assertThat(RuleJson.sha256(RuleJson.canonical(RuleProperties.defaults().snapshot()))).isEqualTo(defaults);
    }

    @Test
    void theSnapshotListsEverySettingAsText() {
        Map<String, Object> snapshot = RuleProperties.defaults().snapshot();

        assertThat(snapshot).containsEntry("roundOffAllowance", "1.00").containsEntry("timeZone", "Asia/Kolkata").containsEntry("minDescriptionWords", 2)
                .containsEntry("variancePercentThreshold", "10.00").hasSize(11);
    }
}
