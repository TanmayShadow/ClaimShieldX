package com.example.ClaimShieldX.investigation;

import com.example.ClaimShieldX.investigation.config.InvestigationProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InvestigationPropertiesTests {
    private static InvestigationProperties with(int maxAttempts, int daily, Duration initial, Duration max, double jitter, Duration lease, Duration heartbeat, Duration recheck, int note) {
        return new InvestigationProperties(maxAttempts, daily, initial, max, jitter, lease, heartbeat, recheck, note, null);
    }

    @Test
    void theDefaultsAreTheDocumentedOnes() {
        InvestigationProperties p = InvestigationProperties.defaults();

        assertThat(p.maxAttempts()).isEqualTo(3);
        assertThat(p.dailyLimitPerOrganization()).isEqualTo(20);
        assertThat(p.backoffInitial()).isEqualTo(Duration.ofSeconds(30));
        assertThat(p.backoffMax()).isEqualTo(Duration.ofMinutes(10));
        assertThat(p.lease()).isEqualTo(Duration.ofMinutes(3));
        assertThat(p.heartbeat()).isEqualTo(Duration.ofSeconds(30));
        assertThat(p.worker().threads()).isEqualTo(2);
        assertThat(p.worker().enabled()).isTrue();
    }

    @Test
    void limitsMustBePositive() {
        Duration s = Duration.ofSeconds(1);
        assertThatThrownBy(() -> with(0, 20, s, s, 0.2, Duration.ofMinutes(3), s, s, 500)).hasMessageContaining("limits must be positive");
        assertThatThrownBy(() -> with(3, 0, s, s, 0.2, Duration.ofMinutes(3), s, s, 500)).hasMessageContaining("limits must be positive");
        assertThatThrownBy(() -> with(3, 20, s, s, 0.2, Duration.ofMinutes(3), s, s, 0)).hasMessageContaining("limits must be positive");
    }

    @Test
    void theBackoffCeilingMayNotBeShorterThanTheStartAndJitterIsAShare() {
        Duration m = Duration.ofMinutes(1);
        assertThatThrownBy(() -> with(3, 20, Duration.ofMinutes(2), m, 0.2, Duration.ofMinutes(3), Duration.ofSeconds(30), Duration.ofSeconds(15), 500)).hasMessageContaining("backoff-max");
        assertThatThrownBy(() -> with(3, 20, Duration.ZERO, m, 0.2, Duration.ofMinutes(3), Duration.ofSeconds(30), Duration.ofSeconds(15), 500)).hasMessageContaining("backoff-max");
        assertThatThrownBy(() -> with(3, 20, Duration.ofSeconds(1), m, 1.5, Duration.ofMinutes(3), Duration.ofSeconds(30), Duration.ofSeconds(15), 500)).hasMessageContaining("backoff-jitter");
        assertThatThrownBy(() -> with(3, 20, Duration.ofSeconds(1), m, -0.1, Duration.ofMinutes(3), Duration.ofSeconds(30), Duration.ofSeconds(15), 500)).hasMessageContaining("backoff-jitter");
    }

    @Test
    void theHeartbeatMustBeShorterThanTheLeaseOrTheLeaseWouldLapseBetweenBeats() {
        Duration m = Duration.ofMinutes(1);
        assertThatThrownBy(() -> with(3, 20, Duration.ofSeconds(1), m, 0.2, Duration.ofSeconds(30), Duration.ofSeconds(30), Duration.ofSeconds(15), 500)).hasMessageContaining("heartbeat");
        assertThatThrownBy(() -> with(3, 20, Duration.ofSeconds(1), m, 0.2, Duration.ofSeconds(30), Duration.ofSeconds(45), Duration.ofSeconds(15), 500)).hasMessageContaining("heartbeat");
        assertThatThrownBy(() -> with(3, 20, Duration.ofSeconds(1), m, 0.2, Duration.ofSeconds(30), Duration.ZERO, Duration.ofSeconds(15), 500)).hasMessageContaining("heartbeat");
        assertThatThrownBy(() -> with(3, 20, Duration.ofSeconds(1), m, 0.2, Duration.ofSeconds(30), Duration.ofSeconds(10), Duration.ZERO, 500)).hasMessageContaining("re-check");
    }

    @Test
    void theWorkerNeedsAPositivePollIntervalAndAtLeastOneThread() {
        assertThatThrownBy(() -> new InvestigationProperties.Worker(true, Duration.ZERO, 2)).hasMessageContaining("poll interval");
        assertThatThrownBy(() -> new InvestigationProperties.Worker(true, Duration.ofSeconds(1), 0)).hasMessageContaining("thread");
    }
}
