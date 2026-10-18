package com.example.ClaimShieldX.investigation.work;

import com.example.ClaimShieldX.investigation.config.InvestigationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.random.RandomGenerator;

/**
 * Exponential backoff with jitter: the delay before attempt n+1 doubles from the initial delay up to a ceiling, and each delay is
 * randomly shortened or lengthened by up to the jitter fraction so that many retries do not arrive at the same moment.
 */
@Component
public class Backoff {
    private final InvestigationProperties properties;
    private final RandomGenerator random;

    public Backoff(InvestigationProperties properties, RandomGenerator investigationRandom) {
        this.properties = properties;
        this.random = investigationRandom;
    }

    /** @param failedAttempt the attempt that just failed, starting at 1 */
    public Duration delayAfter(int failedAttempt) {
        long initial = properties.backoffInitial().toMillis();
        long ceiling = properties.backoffMax().toMillis();
        double base = Math.min((double) ceiling, initial * Math.pow(2, Math.max(0, failedAttempt - 1)));
        double factor = 1 + (random.nextDouble() * 2 - 1) * properties.backoffJitter();
        return Duration.ofMillis(Math.max(1, Math.round(base * factor)));
    }
}
