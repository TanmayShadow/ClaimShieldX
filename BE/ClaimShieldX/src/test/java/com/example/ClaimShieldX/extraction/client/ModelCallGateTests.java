package com.example.ClaimShieldX.extraction.client;

import com.example.ClaimShieldX.extraction.config.ExtractionProperties;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

@org.junit.jupiter.api.Timeout(value = 30, unit = java.util.concurrent.TimeUnit.SECONDS)
class ModelCallGateTests {
    private static ExtractionProperties properties(int perMinute, int concurrent) {
        return new ExtractionProperties("fake", "m", List.of(), "s", "1", "LOW", 8192, Duration.ofSeconds(60), 3, Duration.ofSeconds(1),
                Duration.ofSeconds(1), perMinute, concurrent, 1000L, 40, 200, 5, Duration.ofMinutes(5), new BigDecimal("1.00"), ZoneId.of("UTC"));
    }

    /** A clock that only moves when the gate sleeps, so rate limiting is tested without real time. */
    private static final class FakeTime {
        final AtomicLong nanos = new AtomicLong(1_000_000_000L);
        final List<Duration> sleeps = new ArrayList<>();
        final Sleeper sleeper = duration -> {
            sleeps.add(duration);
            nanos.addAndGet(duration.toNanos());
        };
    }

    @Test
    void callsUpToTheLimitStartAtOnce() throws Exception {
        FakeTime time = new FakeTime();
        ModelCallGate gate = new ModelCallGate(properties(3, 10), time.sleeper, time.nanos::get);

        for (int i = 0; i < 3; i++) {
            gate.acquire();
            gate.release();
        }

        assertThat(time.sleeps).isEmpty();
    }

    @Test
    void theCallOverTheLimitWaitsUntilTheOldestLeavesTheWindow() throws Exception {
        FakeTime time = new FakeTime();
        ModelCallGate gate = new ModelCallGate(properties(3, 10), time.sleeper, time.nanos::get);
        for (int i = 0; i < 3; i++) {
            gate.acquire();
            gate.release();
            time.nanos.addAndGet(Duration.ofSeconds(10).toNanos());
        }

        gate.acquire();
        gate.release();

        // The first call was 30 seconds ago, so 30 more seconds until it is out of the 60-second window.
        assertThat(time.sleeps).hasSize(1);
        assertThat(time.sleeps.get(0)).isBetween(Duration.ofSeconds(29), Duration.ofSeconds(31));
    }

    @Test
    void theWindowSlidesSoALongQuietPeriodClearsTheCount() throws Exception {
        FakeTime time = new FakeTime();
        ModelCallGate gate = new ModelCallGate(properties(2, 10), time.sleeper, time.nanos::get);
        gate.acquire();
        gate.release();
        gate.acquire();
        gate.release();
        time.nanos.addAndGet(Duration.ofSeconds(61).toNanos());

        gate.acquire();
        gate.release();
        gate.acquire();
        gate.release();

        assertThat(time.sleeps).isEmpty();
    }

    @Test
    void neverMoreCallsAtOnceThanTheConcurrencyLimit() throws Exception {
        ModelCallGate gate = new ModelCallGate(properties(1000, 1), d -> { }, System::nanoTime);
        gate.acquire();
        CountDownLatch second = new CountDownLatch(1);
        Thread other = new Thread(() -> {
            try {
                gate.acquire();
                second.countDown();
                gate.release();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });
        other.start();

        assertThat(second.await(200, TimeUnit.MILLISECONDS)).as("second call must wait while the first holds the gate").isFalse();
        gate.release();
        assertThat(second.await(2, TimeUnit.SECONDS)).as("second call proceeds after release").isTrue();
        other.join(2000);
    }

    @Test
    void anInterruptedWaitGivesTheConcurrencyPermitBack() throws Exception {
        FakeTime time = new FakeTime();
        java.util.concurrent.atomic.AtomicBoolean interruptNext = new java.util.concurrent.atomic.AtomicBoolean(true);
        Sleeper flaky = duration -> {
            if (interruptNext.getAndSet(false)) {
                throw new InterruptedException();
            }
            time.sleeper.sleep(duration);
        };
        ModelCallGate gate = new ModelCallGate(properties(1, 1), flaky, time.nanos::get);
        gate.acquire();
        gate.release();

        // Over the per-minute limit, so the gate sleeps, and that sleep is interrupted.
        org.assertj.core.api.Assertions.assertThatThrownBy(gate::acquire).isInstanceOf(InterruptedException.class);

        // With only one concurrency permit, a leaked permit would make this block forever.
        org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
            gate.acquire();
            gate.release();
        });
    }
}
