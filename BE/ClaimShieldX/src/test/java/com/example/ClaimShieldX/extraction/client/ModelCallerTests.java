package com.example.ClaimShieldX.extraction.client;

import com.example.ClaimShieldX.documents.model.DocumentType;
import com.example.ClaimShieldX.extraction.client.ModelCallException.Category;
import com.example.ClaimShieldX.extraction.config.ExtractionProperties;
import com.example.ClaimShieldX.extraction.model.ExtractionFailureCode;
import com.example.ClaimShieldX.extraction.support.FakeExtractionModelClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static com.example.ClaimShieldX.extraction.support.TestDocuments.invoice;
import static com.example.ClaimShieldX.extraction.support.TestDocuments.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Retry, backoff, fallback and the hard bounds on model calls, tested against a scripted model with no real waiting. */
@org.junit.jupiter.api.Timeout(value = 30, unit = java.util.concurrent.TimeUnit.SECONDS)
class ModelCallerTests {
    private static final byte[] PDF = "%PDF-1.4 test".getBytes();
    private final String valid = json(invoice("G-1", "V-1", "12/09/2026"));

    private FakeExtractionModelClient model;
    private final List<Duration> sleeps = new ArrayList<>();
    private ModelCaller caller;

    private static ExtractionProperties properties(int attempts, List<String> fallbacks, Duration initial, Duration max) {
        return new ExtractionProperties("fake", "primary-model", fallbacks, "itemized-cost-1", "1", "LOW", 8192, Duration.ofSeconds(60), attempts,
                initial, max, 1000, 4, 14_680_064L, 40, 200, 5, Duration.ofMinutes(5), new BigDecimal("1.00"), ZoneId.of("Asia/Kolkata"));
    }

    private ModelCaller caller(ExtractionProperties properties) {
        Sleeper sleeper = sleeps::add;
        return new ModelCaller(properties, model, new ModelCallGate(properties, sleeper, System::nanoTime), sleeper, () -> 1.0);
    }

    @BeforeEach
    void setUp() {
        model = new FakeExtractionModelClient();
        sleeps.clear();
        caller = caller(properties(3, List.of("fallback-model"), Duration.ofSeconds(2), Duration.ofSeconds(30)));
    }

    private ModelAnswer call() throws ModelFailure {
        return caller.call(DocumentType.FINAL_INVOICE, PDF, "application/pdf");
    }

    private static ModelCallException failure(Category category) {
        return new ModelCallException(category, category.name(), null);
    }

    // ------------------------------------------------------------ the good paths

    @Test
    void aFirstTimeSuccessUsesOneCallAndNoWaiting() throws Exception {
        model.thenReturn(valid);

        ModelAnswer answer = call();

        assertThat(model.callCount()).isEqualTo(1);
        assertThat(answer.modelCalls()).isEqualTo(1);
        assertThat(answer.modelId()).isEqualTo("primary-model");
        assertThat(answer.inputTokens()).isEqualTo(1200);
        assertThat(answer.outputTokens()).isEqualTo(450);
        assertThat(answer.document().lineItems()).hasSize(3);
        assertThat(sleeps).isEmpty();
    }

    @Test
    void theRequestCarriesTheDocumentTheKindAndTheVersionsButNothingElse() throws Exception {
        model.thenReturn(valid);

        call();

        ModelRequest request = model.requests().get(0);
        assertThat(request.content()).isEqualTo(PDF);
        assertThat(request.mimeType()).isEqualTo("application/pdf");
        assertThat(request.documentType()).isEqualTo(DocumentType.FINAL_INVOICE);
        assertThat(request.modelId()).isEqualTo("primary-model");
        assertThat(request.schemaVersion()).isEqualTo("itemized-cost-1");
        assertThat(request.promptVersion()).isEqualTo("1");
    }

    // ------------------------------------------------------------ transient failures are retried, with bounds

    @Test
    void aTransientFailureIsRetriedAfterABackoffAndThenSucceeds() throws Exception {
        model.thenFail(failure(Category.TRANSIENT)).thenReturn(valid);

        ModelAnswer answer = call();

        assertThat(answer.modelCalls()).isEqualTo(2);
        assertThat(sleeps).containsExactly(Duration.ofSeconds(2));
    }

    @Test
    void theBackoffDoublesEachTimeAndIsCapped() throws Exception {
        caller = caller(properties(6, List.of(), Duration.ofSeconds(2), Duration.ofSeconds(10)));
        for (int i = 0; i < 5; i++) {
            model.thenFail(failure(Category.TRANSIENT));
        }
        model.thenReturn(valid);

        call();

        assertThat(sleeps).containsExactly(Duration.ofSeconds(2), Duration.ofSeconds(4), Duration.ofSeconds(8), Duration.ofSeconds(10), Duration.ofSeconds(10));
    }

    @Test
    void jitterNeverLengthensTheBackoffAndNeverShrinksItBelowHalf() throws Exception {
        for (double jitter : new double[]{0.0, 0.5, 1.0}) {
            sleeps.clear();
            model.reset();
            ExtractionProperties p = properties(2, List.of(), Duration.ofSeconds(2), Duration.ofSeconds(30));
            Sleeper sleeper = sleeps::add;
            ModelCaller jittered = new ModelCaller(p, model, new ModelCallGate(p, sleeper, System::nanoTime), sleeper, () -> jitter);
            model.thenFail(failure(Category.TRANSIENT)).thenReturn(valid);

            jittered.call(DocumentType.FINAL_INVOICE, PDF, "application/pdf");

            assertThat(sleeps.get(0)).isBetween(Duration.ofSeconds(1), Duration.ofSeconds(2));
        }
    }

    @Test
    void aRateLimitWaitsAtLeastAsLongAsTheServiceAsked() throws Exception {
        model.thenFail(new ModelCallException(Category.RATE_LIMITED, "slow down", null, Duration.ofSeconds(12))).thenReturn(valid);

        call();

        assertThat(sleeps).containsExactly(Duration.ofSeconds(12));
    }

    @Test
    void aRequestedWaitLongerThanWeAreWillingToHoldGivesUpOnThatModelAtOnce() throws Exception {
        model.thenFail(new ModelCallException(Category.RATE_LIMITED, "later", null, Duration.ofMinutes(5))).thenReturn(valid);

        ModelAnswer answer = call();

        assertThat(answer.modelId()).isEqualTo("fallback-model");
        assertThat(model.callCount()).isEqualTo(2);
        assertThat(sleeps).isEmpty();
    }

    @Test
    void theNumberOfCallsIsAlwaysBounded() {
        for (int i = 0; i < 100; i++) {
            model.thenFail(failure(Category.TRANSIENT));
        }

        assertThatThrownBy(this::call).isInstanceOfSatisfying(ModelFailure.class, f -> {
            assertThat(f.code()).isEqualTo(ExtractionFailureCode.MODEL_UNAVAILABLE);
            assertThat(f.modelCalls()).isEqualTo(6); // 3 attempts on each of the 2 models
        });
        assertThat(model.callCount()).isEqualTo(6);
        assertThat(sleeps).hasSize(4); // no wait after the last attempt on a model
    }

    // ------------------------------------------------------------ permanent failures are not retried

    @Test
    void anInputTheModelRejectsIsNeverRetriedAndNeverSentToAnotherModel() {
        model.thenFail(failure(Category.REJECTED_INPUT));

        assertThatThrownBy(this::call).isInstanceOfSatisfying(ModelFailure.class, f -> {
            assertThat(f.code()).isEqualTo(ExtractionFailureCode.MODEL_REJECTED_INPUT);
            assertThat(f.modelCalls()).isEqualTo(1);
        });
        assertThat(model.callCount()).isEqualTo(1);
    }

    @Test
    void aBlockedDocumentAndABadConfigurationAreNeverRetried() {
        model.thenFail(failure(Category.BLOCKED));
        assertThatThrownBy(this::call).isInstanceOfSatisfying(ModelFailure.class, f -> assertThat(f.code()).isEqualTo(ExtractionFailureCode.MODEL_BLOCKED));

        model.reset();
        model.thenFail(failure(Category.NOT_CONFIGURED));
        assertThatThrownBy(this::call).isInstanceOfSatisfying(ModelFailure.class, f -> assertThat(f.code()).isEqualTo(ExtractionFailureCode.MODEL_NOT_CONFIGURED));
        assertThat(model.callCount()).isEqualTo(1);
    }

    // ------------------------------------------------------------ quota and the model chain

    @Test
    void anExhaustedDailyQuotaMovesStraightToTheNextModel() throws Exception {
        model.thenFail(failure(Category.QUOTA_EXHAUSTED)).thenReturn(valid);

        ModelAnswer answer = call();

        assertThat(answer.modelId()).isEqualTo("fallback-model");
        assertThat(answer.modelCalls()).isEqualTo(2);
        assertThat(model.requests()).extracting(ModelRequest::modelId).containsExactly("primary-model", "fallback-model");
        assertThat(sleeps).isEmpty();
    }

    @Test
    void whenEveryModelIsOutOfQuotaTheFailureSaysRateLimited() {
        model.thenFail(failure(Category.QUOTA_EXHAUSTED)).thenFail(failure(Category.QUOTA_EXHAUSTED));

        assertThatThrownBy(this::call).isInstanceOfSatisfying(ModelFailure.class, f -> {
            assertThat(f.code()).isEqualTo(ExtractionFailureCode.MODEL_RATE_LIMITED);
            assertThat(f.modelCalls()).isEqualTo(2);
            assertThat(f.modelId()).isEqualTo("fallback-model");
        });
    }

    @Test
    void aModelThatKeepsFailingHandsOverToTheFallback() throws Exception {
        model.thenFail(failure(Category.TRANSIENT)).thenFail(failure(Category.TRANSIENT)).thenFail(failure(Category.TRANSIENT)).thenReturn(valid);

        ModelAnswer answer = call();

        assertThat(answer.modelId()).isEqualTo("fallback-model");
        assertThat(answer.modelCalls()).isEqualTo(4);
    }

    @Test
    void withoutFallbacksTheChainIsJustThePrimaryModel() {
        caller = caller(properties(2, List.of(), Duration.ofSeconds(1), Duration.ofSeconds(1)));
        model.thenFail(failure(Category.QUOTA_EXHAUSTED));

        assertThatThrownBy(this::call).isInstanceOfSatisfying(ModelFailure.class, f -> {
            assertThat(f.code()).isEqualTo(ExtractionFailureCode.MODEL_RATE_LIMITED);
            assertThat(f.modelId()).isEqualTo("primary-model");
        });
        assertThat(model.callCount()).isEqualTo(1);
    }

    // ------------------------------------------------------------ unusable output

    @Test
    void anAnswerThatDoesNotMatchTheSchemaIsRetriedAndThenAccepted() throws Exception {
        model.thenReturn("{\"surprise\": true}").thenReturn(valid);

        ModelAnswer answer = call();

        assertThat(answer.modelCalls()).isEqualTo(2);
        assertThat(answer.document().lineItems()).hasSize(3);
    }

    @Test
    void anAnswerThatNeverMatchesTheSchemaEndsAsInvalidOutput() {
        for (int i = 0; i < 10; i++) {
            model.thenReturn("not json at all");
        }

        assertThatThrownBy(this::call).isInstanceOfSatisfying(ModelFailure.class, f -> {
            assertThat(f.code()).isEqualTo(ExtractionFailureCode.MODEL_OUTPUT_INVALID);
            assertThat(f.modelCalls()).isEqualTo(6);
        });
    }

    @Test
    void anAnswerCutOffAtTheTokenLimitIsRetriedAndEndsAsInvalidOutput() throws Exception {
        model.thenFail(failure(Category.TRUNCATED_OUTPUT)).thenReturn(valid);
        assertThat(call().modelCalls()).isEqualTo(2);

        model.reset();
        for (int i = 0; i < 6; i++) {
            model.thenFail(failure(Category.TRUNCATED_OUTPUT));
        }
        assertThatThrownBy(this::call).isInstanceOfSatisfying(ModelFailure.class, f -> assertThat(f.code()).isEqualTo(ExtractionFailureCode.MODEL_OUTPUT_INVALID));
    }

    @Test
    void theLastFailureDecidesTheReportedCodeWhenTheKindsDiffer() {
        model.thenFail(failure(Category.RATE_LIMITED)).thenFail(failure(Category.RATE_LIMITED)).thenFail(failure(Category.RATE_LIMITED))
                .thenFail(failure(Category.TRANSIENT)).thenFail(failure(Category.TRANSIENT)).thenFail(failure(Category.TRANSIENT));

        assertThatThrownBy(this::call).isInstanceOfSatisfying(ModelFailure.class, f -> assertThat(f.code()).isEqualTo(ExtractionFailureCode.MODEL_UNAVAILABLE));
    }

    // ------------------------------------------------------------ the total time is bounded

    /** A clock that only moves when a call or a wait takes time, so deadlines are tested without real waiting. */
    private final class SlowCalls {
        final java.util.concurrent.atomic.AtomicLong nanos = new java.util.concurrent.atomic.AtomicLong(0);
        final Sleeper sleeper = d -> nanos.addAndGet(d.toNanos());

        ModelCaller caller(Duration requestTimeout, int attempts, List<String> fallbacks) {
            ExtractionProperties p = new ExtractionProperties("fake", "primary-model", fallbacks, "s", "1", "LOW", 8192, requestTimeout, attempts,
                    Duration.ofSeconds(2), Duration.ofSeconds(30), 1000, 4, 1000L, 40, 200, 5, Duration.ofMinutes(5), new BigDecimal("1.00"), ZoneId.of("UTC"));
            return new ModelCaller(p, model, new ModelCallGate(p, sleeper, nanos::get), sleeper, () -> 1.0, nanos::get);
        }

        /** Every call takes this long and then fails with a transient error. */
        void failingCallsTaking(Duration duration, int howMany) {
            for (int i = 0; i < howMany; i++) {
                model.then(request -> {
                    nanos.addAndGet(duration.toNanos());
                    throw failure(Category.TRANSIENT);
                });
            }
        }
    }

    @Test
    void noNewCallStartsOnceTheTotalTimeIsUsedUp() {
        SlowCalls slow = new SlowCalls();
        ModelCaller bounded = slow.caller(Duration.ofSeconds(10), 6, List.of()); // deadline is 20 seconds
        slow.failingCallsTaking(Duration.ofSeconds(8), 6);

        assertThatThrownBy(() -> bounded.call(DocumentType.FINAL_INVOICE, PDF, "application/pdf"))
                .isInstanceOfSatisfying(ModelFailure.class, f -> {
                    assertThat(f.code()).isEqualTo(ExtractionFailureCode.MODEL_UNAVAILABLE);
                    assertThat(f.modelCalls()).isEqualTo(2);
                });
        assertThat(model.callCount()).isEqualTo(2); // 8s, wait 2s, 8s = 18s; the next wait (4s) would pass 20s
    }

    @Test
    void theDeadlineAlsoStopsTheFallbackModelsFromBeingTried() {
        SlowCalls slow = new SlowCalls();
        ModelCaller bounded = slow.caller(Duration.ofSeconds(10), 1, List.of("fallback-model", "third-model"));
        slow.failingCallsTaking(Duration.ofSeconds(25), 3);

        assertThatThrownBy(() -> bounded.call(DocumentType.FINAL_INVOICE, PDF, "application/pdf"))
                .isInstanceOfSatisfying(ModelFailure.class, f -> assertThat(f.modelCalls()).isEqualTo(1));
        assertThat(model.callCount()).isEqualTo(1);
    }

    @Test
    void aFirstCallIsAlwaysMadeAndASlowButSuccessfulCallIsKept() throws Exception {
        SlowCalls slow = new SlowCalls();
        ModelCaller bounded = slow.caller(Duration.ofSeconds(10), 3, List.of());
        model.then(request -> {
            slow.nanos.addAndGet(Duration.ofSeconds(60).toNanos()); // far beyond the deadline, but it answered
            return new ModelResponse(valid, 1, 1);
        });

        assertThat(bounded.call(DocumentType.FINAL_INVOICE, PDF, "application/pdf").modelCalls()).isEqualTo(1);
    }

    @Test
    void theProcessingTimeoutIsLongerThanTheWorstRun() {
        ExtractionProperties p = ExtractionProperties.defaults();

        // Worst case: calls until the deadline, then one more call already in flight for a full request timeout.
        assertThat(p.processingTimeout()).isGreaterThan(p.deadline().plus(p.requestTimeout()));
    }

    // ------------------------------------------------------------ safety of the machinery

    @Test
    void anInterruptWhileWaitingStopsTheCallsAndKeepsTheInterruptFlag() {
        Sleeper interrupted = d -> {
            throw new InterruptedException();
        };
        ExtractionProperties p = properties(3, List.of(), Duration.ofSeconds(2), Duration.ofSeconds(30));
        ModelCaller impatient = new ModelCaller(p, model, new ModelCallGate(p, interrupted, System::nanoTime), interrupted, () -> 1.0);
        model.thenFail(failure(Category.TRANSIENT));

        try {
            assertThatThrownBy(() -> impatient.call(DocumentType.FINAL_INVOICE, PDF, "application/pdf"))
                    .isInstanceOfSatisfying(ModelFailure.class, f -> assertThat(f.code()).isEqualTo(ExtractionFailureCode.INTERNAL_ERROR));
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted(); // do not leak the flag into other tests
        }
        assertThat(model.callCount()).isEqualTo(1);
    }

    @Test
    void theGateIsReleasedAfterEveryFailureSoLaterCallsAreNotBlocked() throws Exception {
        ExtractionProperties p = properties(2, List.of(), Duration.ofMillis(1), Duration.ofMillis(1));
        ModelCaller single = new ModelCaller(new ExtractionProperties("fake", "primary-model", List.of(), "s", "1", "LOW", 8192, Duration.ofSeconds(60), 2,
                Duration.ofMillis(1), Duration.ofMillis(1), 1000, 1, 1000L, 40, 200, 5, Duration.ofMinutes(5), new BigDecimal("1.00"), ZoneId.of("UTC")),
                model, new ModelCallGate(new ExtractionProperties("fake", "primary-model", List.of(), "s", "1", "LOW", 8192, Duration.ofSeconds(60), 2,
                        Duration.ofMillis(1), Duration.ofMillis(1), 1000, 1, 1000L, 40, 200, 5, Duration.ofMinutes(5), new BigDecimal("1.00"), ZoneId.of("UTC")),
                        sleeps::add, System::nanoTime), sleeps::add, () -> 1.0);
        for (int round = 0; round < 5; round++) {
            model.reset();
            model.thenFail(failure(Category.TRANSIENT)).thenFail(failure(Category.TRANSIENT));
            assertThatThrownBy(() -> single.call(DocumentType.FINAL_INVOICE, PDF, "application/pdf")).isInstanceOf(ModelFailure.class);
        }
        model.reset();
        model.thenReturn(valid);

        assertThat(single.call(DocumentType.FINAL_INVOICE, PDF, "application/pdf").modelCalls()).isEqualTo(1);
        assertThat(p.maxModelAttempts()).isEqualTo(2);
    }
}
