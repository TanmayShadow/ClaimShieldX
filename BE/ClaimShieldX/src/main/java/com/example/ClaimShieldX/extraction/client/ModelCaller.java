package com.example.ClaimShieldX.extraction.client;

import com.example.ClaimShieldX.documents.model.DocumentType;
import com.example.ClaimShieldX.extraction.client.ModelCallException.Category;
import com.example.ClaimShieldX.extraction.config.ExtractionProperties;
import com.example.ClaimShieldX.extraction.model.ExtractionFailureCode;
import com.example.ClaimShieldX.extraction.schema.ModelDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;
import java.util.function.LongSupplier;

/**
 * Gets one usable answer out of the provider, within strict limits. Transient failures, rate limits, cut-off output and
 * unparseable output are retried a few times with exponential backoff and jitter. A permanent input or configuration
 * error is never retried. When a model is out of quota or keeps failing, the next model in the chain is tried. Nothing
 * here loops without a bound.
 */
@Slf4j
@Component
public class ModelCaller {
    private final ExtractionProperties properties;
    private final ExtractionModelClient client;
    private final ModelCallGate gate;
    private final Sleeper sleeper;
    private final DoubleSupplier jitter;
    private final LongSupplier nanoClock;

    @Autowired
    public ModelCaller(ExtractionProperties properties, ExtractionModelClient client, ModelCallGate gate, Sleeper sleeper) {
        this(properties, client, gate, sleeper, () -> ThreadLocalRandom.current().nextDouble(), System::nanoTime);
    }

    ModelCaller(ExtractionProperties properties, ExtractionModelClient client, ModelCallGate gate, Sleeper sleeper, DoubleSupplier jitter) {
        this(properties, client, gate, sleeper, jitter, System::nanoTime);
    }

    ModelCaller(ExtractionProperties properties, ExtractionModelClient client, ModelCallGate gate, Sleeper sleeper, DoubleSupplier jitter, LongSupplier nanoClock) {
        this.properties = properties;
        this.client = client;
        this.gate = gate;
        this.sleeper = sleeper;
        this.jitter = jitter;
        this.nanoClock = nanoClock;
    }

    public ModelAnswer call(DocumentType type, byte[] content, String mimeType) throws ModelFailure {
        List<String> chain = properties.modelChain();
        int calls = 0;
        ExtractionFailureCode lastFailure = ExtractionFailureCode.MODEL_UNAVAILABLE;
        String lastModel = chain.get(0);
        Throwable lastCause = null;
        long deadline = nanoClock.getAsLong() + properties.deadline().toNanos();

        for (String model : chain) {
            lastModel = model;
            ModelRequest request = new ModelRequest(type, content, mimeType, model, properties.schemaVersion(), properties.promptVersion());
            for (int attempt = 1; attempt <= properties.maxModelAttempts(); attempt++) {
                if (calls > 0 && nanoClock.getAsLong() >= deadline) {
                    log.warn("Giving up on the extraction: the total time allowed for model calls ran out after {} call(s)", calls);
                    throw new ModelFailure(lastFailure, lastModel, calls, lastCause);
                }
                calls++;
                Duration wait;
                try {
                    ModelResponse response = callOnce(request);
                    ModelDocument document = ModelOutputParser.parse(response.json());
                    return new ModelAnswer(document, model, calls, response.inputTokens(), response.outputTokens());
                } catch (ModelOutputParser.MalformedOutputException e) {
                    lastFailure = ExtractionFailureCode.MODEL_OUTPUT_INVALID;
                    lastCause = e;
                    log.warn("Model {} returned output that does not match the schema (attempt {})", model, attempt);
                    wait = backoff(attempt, null);
                } catch (ModelCallException e) {
                    lastCause = e;
                    switch (e.category()) {
                        case REJECTED_INPUT -> throw new ModelFailure(ExtractionFailureCode.MODEL_REJECTED_INPUT, model, calls, e);
                        case BLOCKED -> throw new ModelFailure(ExtractionFailureCode.MODEL_BLOCKED, model, calls, e);
                        case NOT_CONFIGURED -> throw new ModelFailure(ExtractionFailureCode.MODEL_NOT_CONFIGURED, model, calls, e);
                        default -> {
                        }
                    }
                    lastFailure = failureFor(e.category());
                    if (e.category() == Category.QUOTA_EXHAUSTED) {
                        log.warn("Model {} is out of quota; trying the next model if there is one", model);
                        break;
                    }
                    log.warn("Model {} call failed: {} (attempt {})", model, e.category(), attempt);
                    if (e.retryAfter() != null && e.retryAfter().compareTo(properties.retryMaxDelay()) > 0) {
                        break; // the service asked for a longer wait than we are willing to hold a request
                    }
                    wait = backoff(attempt, e.retryAfter());
                }
                if (attempt < properties.maxModelAttempts()) {
                    if (nanoClock.getAsLong() + wait.toNanos() >= deadline) {
                        log.warn("Giving up on the extraction: waiting {} ms would run past the total time allowed for model calls", wait.toMillis());
                        throw new ModelFailure(lastFailure, lastModel, calls, lastCause);
                    }
                    pause(wait, lastModel, calls);
                }
            }
        }
        throw new ModelFailure(lastFailure, lastModel, calls, lastCause);
    }

    private static ExtractionFailureCode failureFor(Category category) {
        return switch (category) {
            case QUOTA_EXHAUSTED, RATE_LIMITED -> ExtractionFailureCode.MODEL_RATE_LIMITED;
            case TRUNCATED_OUTPUT -> ExtractionFailureCode.MODEL_OUTPUT_INVALID;
            default -> ExtractionFailureCode.MODEL_UNAVAILABLE;
        };
    }

    private ModelResponse callOnce(ModelRequest request) throws ModelCallException {
        try {
            gate.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ModelCallException(Category.TRANSIENT, "Interrupted while waiting to call the model", e);
        }
        try {
            return client.extract(request);
        } finally {
            gate.release();
        }
    }

    private void pause(Duration wait, String model, int calls) throws ModelFailure {
        try {
            sleeper.sleep(wait);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ModelFailure(ExtractionFailureCode.INTERNAL_ERROR, model, calls, e);
        }
    }

    /** Exponential backoff with equal jitter, never shorter than the delay the service asked for. */
    Duration backoff(int attempt, Duration retryAfter) {
        long initial = properties.retryInitialDelay().toMillis();
        long max = properties.retryMaxDelay().toMillis();
        long exponential = Math.min(max, initial * (1L << Math.min(attempt - 1, 20)));
        long jittered = (long) (exponential * (0.5 + 0.5 * jitter.getAsDouble()));
        long wait = retryAfter == null ? jittered : Math.max(jittered, retryAfter.toMillis());
        return Duration.ofMillis(Math.min(wait, Math.max(max, retryAfter == null ? 0 : retryAfter.toMillis())));
    }
}
