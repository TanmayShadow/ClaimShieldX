package com.example.ClaimShieldX.history;

import com.example.ClaimShieldX.history.config.HistoryProperties;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HistoryPropertiesTests {
    @Test
    void theDefaultsMatchTheDocumentedLimits() {
        HistoryProperties p = HistoryProperties.defaults();

        assertThat(p.maxFileBytes()).isEqualTo(5L * 1024 * 1024);
        assertThat(p.maxRows()).isEqualTo(10_000);
        assertThat(p.dateOnlyZone()).isEqualTo(ZoneId.of("Asia/Kolkata"));
        assertThat(p.maxMatches()).isEqualTo(50);
        assertThat(p.maxCandidates()).isEqualTo(500);
        assertThat(p.staleProcessingAfter()).isEqualTo(java.time.Duration.ofMinutes(15));
    }

    @Test
    void everyLimitMustBePositiveSoABadSettingStopsTheApplicationFromStarting() {
        assertThatThrownBy(() -> new HistoryProperties(0, 10, ZoneId.of("UTC"), 5, 5, java.time.Duration.ofMinutes(1))).hasMessageContaining("positive");
        assertThatThrownBy(() -> new HistoryProperties(10, 0, ZoneId.of("UTC"), 5, 5, java.time.Duration.ofMinutes(1))).hasMessageContaining("positive");
        assertThatThrownBy(() -> new HistoryProperties(10, 10, ZoneId.of("UTC"), 0, 5, java.time.Duration.ofMinutes(1))).hasMessageContaining("positive");
        assertThatThrownBy(() -> new HistoryProperties(10, 10, ZoneId.of("UTC"), 5, -1, java.time.Duration.ofMinutes(1))).hasMessageContaining("positive");
        assertThatThrownBy(() -> new HistoryProperties(10, 10, ZoneId.of("UTC"), 5, 5, java.time.Duration.ZERO)).hasMessageContaining("positive");
    }
}
