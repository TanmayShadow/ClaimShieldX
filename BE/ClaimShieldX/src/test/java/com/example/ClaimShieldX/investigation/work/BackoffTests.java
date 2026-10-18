package com.example.ClaimShieldX.investigation.work;

import com.example.ClaimShieldX.investigation.config.InvestigationProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.random.RandomGenerator;

import static org.assertj.core.api.Assertions.assertThat;

class BackoffTests {
    /** A "random" generator that always answers the same, so the delay is exact. 0.5 means no jitter. */
    private static RandomGenerator always(double value) {
        return new RandomGenerator() {
            @Override
            public long nextLong() {
                return 0;
            }

            @Override
            public double nextDouble() {
                return value;
            }
        };
    }

    private final InvestigationProperties properties = InvestigationProperties.defaults();   // 30s initial, 10m ceiling, 20% jitter

    @Test
    void theDelayDoublesWithEachFailedAttempt() {
        Backoff backoff = new Backoff(properties, always(0.5));

        assertThat(backoff.delayAfter(1)).isEqualTo(Duration.ofSeconds(30));
        assertThat(backoff.delayAfter(2)).isEqualTo(Duration.ofSeconds(60));
        assertThat(backoff.delayAfter(3)).isEqualTo(Duration.ofSeconds(120));
        assertThat(backoff.delayAfter(4)).isEqualTo(Duration.ofSeconds(240));
    }

    @Test
    void theDelayNeverExceedsTheCeilingBeforeJitter() {
        Backoff backoff = new Backoff(properties, always(0.5));

        assertThat(backoff.delayAfter(6)).isEqualTo(Duration.ofMinutes(10));
        assertThat(backoff.delayAfter(30)).isEqualTo(Duration.ofMinutes(10));
        assertThat(backoff.delayAfter(1000)).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void jitterShortensOrLengthensEachDelayByAtMostTheConfiguredShare() {
        assertThat(new Backoff(properties, always(0.0)).delayAfter(1)).isEqualTo(Duration.ofSeconds(24));      // 30 s - 20%
        assertThat(new Backoff(properties, always(1.0)).delayAfter(1)).isEqualTo(Duration.ofSeconds(36));      // 30 s + 20%
        assertThat(new Backoff(properties, always(0.0)).delayAfter(3)).isEqualTo(Duration.ofSeconds(96));
    }

    @Test
    void manyRandomDelaysStayInsideTheBandAndAreNotAllEqual() {
        Backoff backoff = new Backoff(properties, new java.security.SecureRandom());
        long min = Long.MAX_VALUE;
        long max = 0;
        for (int i = 0; i < 500; i++) {
            long millis = backoff.delayAfter(2).toMillis();
            min = Math.min(min, millis);
            max = Math.max(max, millis);
        }

        assertThat(min).isGreaterThanOrEqualTo(48_000);
        assertThat(max).isLessThanOrEqualTo(72_000);
        assertThat(max).isGreaterThan(min);
    }

    @Test
    void noJitterGivesTheExactDelay() {
        InvestigationProperties exact = new InvestigationProperties(3, 20, Duration.ofSeconds(10), Duration.ofMinutes(1), 0.0, Duration.ofMinutes(3), Duration.ofSeconds(30),
                Duration.ofSeconds(15), 500, null);

        assertThat(new Backoff(exact, always(0.0)).delayAfter(2)).isEqualTo(Duration.ofSeconds(20));
        assertThat(new Backoff(exact, always(1.0)).delayAfter(2)).isEqualTo(Duration.ofSeconds(20));
    }

    @Test
    void aZeroOrNegativeAttemptIsTreatedAsTheFirst() {
        Backoff backoff = new Backoff(properties, always(0.5));

        assertThat(backoff.delayAfter(0)).isEqualTo(Duration.ofSeconds(30));
        assertThat(backoff.delayAfter(-3)).isEqualTo(Duration.ofSeconds(30));
    }
}
