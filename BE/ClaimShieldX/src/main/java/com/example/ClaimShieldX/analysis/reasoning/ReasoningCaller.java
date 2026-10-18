package com.example.ClaimShieldX.analysis.reasoning;

import com.example.ClaimShieldX.extraction.client.ModelCallException;
import com.example.ClaimShieldX.extraction.client.ModelCallException.Category;
import com.example.ClaimShieldX.extraction.client.ModelCallGate;
import com.example.ClaimShieldX.extraction.client.ModelResponse;
import com.example.ClaimShieldX.extraction.client.Sleeper;
import com.example.ClaimShieldX.extraction.config.ExtractionProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * One usable, validated answer from the model within strict limits. Transient failures, rate limits, cut-off or unparseable output are
 * retried with exponential backoff and jitter; a daily-quota error moves to the next model of the chain; a permanent error is never
 * retried. Every request passes the same rate gate as document reading, so the project's free quota is respected as a whole.
 */
@Component
public class ReasoningCaller {
    private static final Logger log = LoggerFactory.getLogger(ReasoningCaller.class);

    /** Turns the model's text into a typed, validated value; throws {@link MalformedOutput} when it cannot. */
    public interface Parser<T> {
        T parse(String json) throws MalformedOutput;
    }

    public static class MalformedOutput extends Exception {
        public MalformedOutput(String message) {
            super(message);
        }
    }

    public record Answer<T>(T value, String modelId, int calls, Integer inputTokens, Integer outputTokens) {
    }

    private final ExtractionProperties properties;
    private final ReasoningModelClient client;
    private final ModelCallGate gate;
    private final Sleeper sleeper;

    public ReasoningCaller(ExtractionProperties properties, ReasoningModelClient client, ModelCallGate gate, Sleeper sleeper) {
        this.properties = properties;
        this.client = client;
        this.gate = gate;
        this.sleeper = sleeper;
    }

    public <T> Answer<T> call(String role, String system, String user, java.util.Map<String, Object> schema, Parser<T> parser) throws ReasoningFailure {
        List<String> chain = properties.modelChain();
        int calls = 0;
        ReasoningFailure.Code last = ReasoningFailure.Code.UNAVAILABLE;
        String lastModel = chain.get(0);
        Throwable lastCause = null;
        long deadline = System.nanoTime() + properties.deadline().toNanos();

        for (String model : chain) {
            lastModel = model;
            for (int attempt = 1; attempt <= properties.maxModelAttempts(); attempt++) {
                if (calls > 0 && System.nanoTime() >= deadline) {
                    throw new ReasoningFailure(last, lastModel, calls, lastCause);
                }
                calls++;
                Duration wait;
                try {
                    ModelResponse response = callOnce(new ReasoningModelClient.Request(role, model, system, user, schema));
                    return new Answer<>(parser.parse(response.json()), model, calls, response.inputTokens(), response.outputTokens());
                } catch (MalformedOutput e) {
                    last = ReasoningFailure.Code.OUTPUT_INVALID;
                    lastCause = e;
                    log.warn("Reasoning role {} on model {} returned unusable output (attempt {})", role, model, attempt);
                    wait = backoff(attempt, null);
                } catch (ModelCallException e) {
                    lastCause = e;
                    switch (e.category()) {
                        case REJECTED_INPUT -> throw new ReasoningFailure(ReasoningFailure.Code.REJECTED, model, calls, e);
                        case BLOCKED -> throw new ReasoningFailure(ReasoningFailure.Code.BLOCKED, model, calls, e);
                        case NOT_CONFIGURED -> throw new ReasoningFailure(ReasoningFailure.Code.NOT_CONFIGURED, model, calls, e);
                        default -> {
                        }
                    }
                    last = e.category() == Category.QUOTA_EXHAUSTED || e.category() == Category.RATE_LIMITED ? ReasoningFailure.Code.RATE_LIMITED
                            : e.category() == Category.TRUNCATED_OUTPUT ? ReasoningFailure.Code.OUTPUT_INVALID : ReasoningFailure.Code.UNAVAILABLE;
                    if (e.category() == Category.QUOTA_EXHAUSTED) {
                        log.warn("Model {} is out of quota for role {}; trying the next model if there is one", model, role);
                        break;
                    }
                    if (e.retryAfter() != null && e.retryAfter().compareTo(properties.retryMaxDelay()) > 0) {
                        break;
                    }
                    wait = backoff(attempt, e.retryAfter());
                }
                if (attempt < properties.maxModelAttempts()) {
                    if (System.nanoTime() + wait.toNanos() >= deadline) {
                        throw new ReasoningFailure(last, lastModel, calls, lastCause);
                    }
                    try {
                        sleeper.sleep(wait);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new ReasoningFailure(ReasoningFailure.Code.UNAVAILABLE, model, calls, e);
                    }
                }
            }
        }
        throw new ReasoningFailure(last, lastModel, calls, lastCause);
    }

    private ModelResponse callOnce(ReasoningModelClient.Request request) throws ModelCallException {
        try {
            gate.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ModelCallException(Category.TRANSIENT, "Interrupted while waiting to call the model", e);
        }
        try {
            return client.generate(request);
        } finally {
            gate.release();
        }
    }

    Duration backoff(int attempt, Duration retryAfter) {
        long initial = properties.retryInitialDelay().toMillis();
        long max = properties.retryMaxDelay().toMillis();
        long exponential = Math.min(max, initial * (1L << Math.min(attempt - 1, 20)));
        long jittered = (long) (exponential * (0.5 + 0.5 * ThreadLocalRandom.current().nextDouble()));
        long wait = retryAfter == null ? jittered : Math.max(jittered, retryAfter.toMillis());
        return Duration.ofMillis(Math.min(wait, Math.max(max, retryAfter == null ? 0 : retryAfter.toMillis())));
    }
}
