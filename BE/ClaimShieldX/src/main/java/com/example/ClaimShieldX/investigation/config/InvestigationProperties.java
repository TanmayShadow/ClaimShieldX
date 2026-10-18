package com.example.ClaimShieldX.investigation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/** Limits and timings of the investigation queue and its worker. */
@ConfigurationProperties("app.investigation")
public record InvestigationProperties(
        /** Attempts an investigation gets before a transient failure is final (the first run counts as attempt 1). */
        @DefaultValue("3") int maxAttempts,
        /** Investigations one organization may start in any 24 hours; retries do not count. Protects the model quota and cost. */
        @DefaultValue("20") int dailyLimitPerOrganization,
        @DefaultValue("30s") Duration backoffInitial,
        @DefaultValue("10m") Duration backoffMax,
        /** Fraction (0 to 1) by which each backoff delay is randomly shortened or lengthened, so retries do not arrive together. */
        @DefaultValue("0.2") double backoffJitter,
        /** How long a worker holds an investigation without renewing it. A crashed worker's job is claimable again after this. */
        @DefaultValue("3m") Duration lease,
        @DefaultValue("30s") Duration heartbeat,
        /** How often a waiting investigation re-checks whether the missing document has arrived. */
        @DefaultValue("15s") Duration waitingRecheck,
        @DefaultValue("500") int noteMaxLength,
        Worker worker
) {
    public record Worker(
            /** Turned off in the tests, which drive the worker by hand. */
            @DefaultValue("true") boolean enabled,
            @DefaultValue("2s") Duration pollInterval,
            /** Investigations one application instance works on at the same time. */
            @DefaultValue("2") int threads
    ) {
        public Worker {
            if (pollInterval.isNegative() || pollInterval.isZero() || threads < 1) {
                throw new IllegalArgumentException("app.investigation.worker needs a positive poll interval and at least one thread");
            }
        }
    }

    public InvestigationProperties {
        if (worker == null) {
            worker = new Worker(true, Duration.ofSeconds(2), 2);
        }
        if (maxAttempts < 1 || dailyLimitPerOrganization < 1 || noteMaxLength < 1) {
            throw new IllegalArgumentException("app.investigation limits must be positive");
        }
        if (backoffInitial.isNegative() || backoffInitial.isZero() || backoffMax.compareTo(backoffInitial) < 0) {
            throw new IllegalArgumentException("app.investigation.backoff-max must not be shorter than backoff-initial, which must be positive");
        }
        if (backoffJitter < 0 || backoffJitter > 1) {
            throw new IllegalArgumentException("app.investigation.backoff-jitter must be between 0 and 1");
        }
        if (lease.isNegative() || lease.isZero() || heartbeat.isNegative() || heartbeat.isZero() || heartbeat.compareTo(lease) >= 0
                || waitingRecheck.isNegative() || waitingRecheck.isZero()) {
            throw new IllegalArgumentException("app.investigation.heartbeat must be positive and shorter than the lease, and the waiting re-check positive");
        }
    }

    public static InvestigationProperties defaults() {
        return new InvestigationProperties(3, 20, Duration.ofSeconds(30), Duration.ofMinutes(10), 0.2, Duration.ofMinutes(3), Duration.ofSeconds(30),
                Duration.ofSeconds(15), 500, null);
    }
}
