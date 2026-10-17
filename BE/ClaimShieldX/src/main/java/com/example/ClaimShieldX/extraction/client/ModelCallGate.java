package com.example.ClaimShieldX.extraction.client;

import com.example.ClaimShieldX.extraction.config.ExtractionProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.concurrent.Semaphore;
import java.util.function.LongSupplier;

/**
 * Keeps this application under the provider's limits: at most N calls at once, and at most M calls in any 60 seconds. It
 * waits instead of failing, so a burst of documents is spread out rather than answered with rate-limit errors.
 */
@Component
public class ModelCallGate {
    private static final long WINDOW_NANOS = Duration.ofMinutes(1).toNanos();

    private final Semaphore concurrency;
    private final int perMinute;
    private final Sleeper sleeper;
    private final LongSupplier nanoClock;
    private final ArrayDeque<Long> starts = new ArrayDeque<>();
    private final Object lock = new Object();

    @Autowired
    public ModelCallGate(ExtractionProperties properties, Sleeper sleeper) {
        this(properties, sleeper, System::nanoTime);
    }

    ModelCallGate(ExtractionProperties properties, Sleeper sleeper, LongSupplier nanoClock) {
        this.concurrency = new Semaphore(properties.maxConcurrentRequests(), true);
        this.perMinute = properties.maxRequestsPerMinute();
        this.sleeper = sleeper;
        this.nanoClock = nanoClock;
    }

    /** Blocks until a call may start. Every successful acquire must be followed by {@link #release()}. */
    public void acquire() throws InterruptedException {
        concurrency.acquire();
        try {
            waitForRate();
        } catch (InterruptedException | RuntimeException e) {
            concurrency.release();
            throw e;
        }
    }

    public void release() {
        concurrency.release();
    }

    private void waitForRate() throws InterruptedException {
        while (true) {
            long wait;
            synchronized (lock) {
                long now = nanoClock.getAsLong();
                while (!starts.isEmpty() && now - starts.peekFirst() >= WINDOW_NANOS) {
                    starts.pollFirst();
                }
                if (starts.size() < perMinute) {
                    starts.addLast(now);
                    return;
                }
                wait = starts.peekFirst() + WINDOW_NANOS - now;
            }
            sleeper.sleep(Duration.ofNanos(Math.max(wait, 1_000_000L)));
        }
    }
}
