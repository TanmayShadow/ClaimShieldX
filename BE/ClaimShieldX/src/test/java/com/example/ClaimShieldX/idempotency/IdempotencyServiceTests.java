package com.example.ClaimShieldX.idempotency;

import com.example.ClaimShieldX.exceptions.IdempotencyKeyReusedException;
import com.example.ClaimShieldX.exceptions.RequestInProgressException;
import com.example.ClaimShieldX.exceptions.RequestValidationException;
import com.example.ClaimShieldX.idempotency.service.IdempotencyService;
import com.example.ClaimShieldX.support.DbFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Not @Transactional on purpose: the service commits in its own transactions, and the races need real concurrency. */
@SpringBootTest
@ActiveProfiles("test")
class IdempotencyServiceTests {
    private static final String OPERATION = "POST /test/operation";

    @Autowired private IdempotencyService service;
    @Autowired private JdbcTemplate jdbc;

    private DbFixtures fixtures;
    private UUID organization;

    @BeforeEach
    void setUp() {
        fixtures = new DbFixtures(jdbc);
        organization = fixtures.organization();
    }

    @AfterEach
    void cleanUp() {
        fixtures.cleanUp();
    }

    private static String key() {
        return "key-" + UUID.randomUUID();
    }

    private static String fingerprint(String seed) {
        return IdempotencyService.fingerprint(seed);
    }

    @Test
    void aNewKeyStartsNewWork() {
        IdempotencyService.Begin begin = service.begin(organization, OPERATION, key(), fingerprint("a"));

        assertThat(begin.mode()).isEqualTo(IdempotencyService.Mode.NEW);
        assertThat(begin.keyId()).isNotNull();
        assertThat(begin.resourceId()).isNull();
    }

    @Test
    void aCompletedKeyReplaysTheStoredResultForTheSameRequest() {
        String key = key();
        UUID resource = UUID.randomUUID();
        IdempotencyService.Begin first = service.begin(organization, OPERATION, key, fingerprint("a"));
        service.complete(first.keyId(), 201, "DOCUMENT", resource);

        IdempotencyService.Begin second = service.begin(organization, OPERATION, key, fingerprint("a"));

        assertThat(second.mode()).isEqualTo(IdempotencyService.Mode.REPLAY);
        assertThat(second.resourceType()).isEqualTo("DOCUMENT");
        assertThat(second.resourceId()).isEqualTo(resource);
    }

    @Test
    void theSameKeyWithAnotherRequestIsRefusedWhetherOrNotTheFirstFinished() {
        String key = key();
        IdempotencyService.Begin first = service.begin(organization, OPERATION, key, fingerprint("a"));

        assertThatThrownBy(() -> service.begin(organization, OPERATION, key, fingerprint("b"))).isInstanceOf(IdempotencyKeyReusedException.class);

        service.complete(first.keyId(), 201, "DOCUMENT", UUID.randomUUID());
        assertThatThrownBy(() -> service.begin(organization, OPERATION, key, fingerprint("b"))).isInstanceOf(IdempotencyKeyReusedException.class);
    }

    @Test
    void aRequestStillInProgressBlocksAnIdenticalRetry() {
        String key = key();
        service.begin(organization, OPERATION, key, fingerprint("a"));

        assertThatThrownBy(() -> service.begin(organization, OPERATION, key, fingerprint("a"))).isInstanceOf(RequestInProgressException.class);
    }

    @Test
    void releasingAKeyLetsTheClientRetryFromScratch() {
        String key = key();
        IdempotencyService.Begin first = service.begin(organization, OPERATION, key, fingerprint("a"));

        service.release(first.keyId());

        assertThat(service.begin(organization, OPERATION, key, fingerprint("different")).mode()).isEqualTo(IdempotencyService.Mode.NEW);
    }

    @Test
    void releasingAnUnknownKeyNeverThrows() {
        service.release(UUID.randomUUID());
        service.release(UUID.randomUUID());
    }

    @Test
    void keysAreScopedPerOrganizationAndPerOperation() {
        String key = key();
        service.begin(organization, OPERATION, key, fingerprint("a"));

        assertThat(service.begin(fixtures.organization(), OPERATION, key, fingerprint("b")).mode()).isEqualTo(IdempotencyService.Mode.NEW);
        assertThat(service.begin(organization, "POST /test/other", key, fingerprint("b")).mode()).isEqualTo(IdempotencyService.Mode.NEW);
    }

    @Test
    void anExpiredKeyIsReplacedEvenForADifferentRequest() {
        String key = key();
        IdempotencyService.Begin first = service.begin(organization, OPERATION, key, fingerprint("a"));
        service.complete(first.keyId(), 201, "DOCUMENT", UUID.randomUUID());
        jdbc.update("update idempotency_keys set expires_at = now() - interval '1 second' where id = ?", first.keyId());

        assertThat(service.begin(organization, OPERATION, key, fingerprint("b")).mode()).isEqualTo(IdempotencyService.Mode.NEW);
    }

    @Test
    void aStaleInProgressKeyIsTakenOverButAFreshOneIsNot() {
        String stale = key();
        String fresh = key();
        IdempotencyService.Begin staleBegin = service.begin(organization, OPERATION, stale, fingerprint("a"));
        service.begin(organization, OPERATION, fresh, fingerprint("a"));
        jdbc.update("update idempotency_keys set created_at = now() - interval '6 minutes' where id = ?", staleBegin.keyId());

        assertThat(service.begin(organization, OPERATION, stale, fingerprint("a")).mode()).isEqualTo(IdempotencyService.Mode.NEW);
        assertThatThrownBy(() -> service.begin(organization, OPERATION, fresh, fingerprint("a"))).isInstanceOf(RequestInProgressException.class);
    }

    @Test
    void whenManyRequestsRaceForTheSameKeyExactlyOneStartsTheWork() throws Exception {
        int parallel = 8;
        String key = key();
        ExecutorService pool = Executors.newFixedThreadPool(parallel);
        try {
            CountDownLatch ready = new CountDownLatch(parallel);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<String>> outcomes = new ArrayList<>();
            for (int i = 0; i < parallel; i++) {
                outcomes.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    try {
                        return service.begin(organization, OPERATION, key, fingerprint("a")).mode().name();
                    } catch (RequestInProgressException e) {
                        return "IN_PROGRESS";
                    }
                }));
            }
            ready.await();
            go.countDown();

            List<String> results = new ArrayList<>();
            for (Future<String> outcome : outcomes) {
                results.add(outcome.get(30, TimeUnit.SECONDS));
            }
            assertThat(results).filteredOn("NEW"::equals).hasSize(1);
            assertThat(results).filteredOn("IN_PROGRESS"::equals).hasSize(parallel - 1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject("select count(*) from idempotency_keys where organization_id = ? and idempotency_key = ?",
                Integer.class, organization, key)).isEqualTo(1);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"short", "has spaces here", "bad/slash/12345", "semi;colon;12345", "caf\u00e9-key-12345"})
    void malformedKeysAreRejected(String key) {
        assertThatThrownBy(() -> IdempotencyService.validateKey(key)).isInstanceOf(RequestValidationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345678", "a1b2c3d4-e5f6-7890-abcd-ef1234567890", "key.with:all_the-allowed.chars1"})
    void wellFormedKeysAreAccepted(String key) {
        IdempotencyService.validateKey(key);
    }

    @Test
    void theLengthLimitsAreExact() {
        IdempotencyService.validateKey("x".repeat(8));
        IdempotencyService.validateKey("x".repeat(128));
        assertThatThrownBy(() -> IdempotencyService.validateKey("x".repeat(7))).isInstanceOf(RequestValidationException.class);
        assertThatThrownBy(() -> IdempotencyService.validateKey("x".repeat(129))).isInstanceOf(RequestValidationException.class);
    }

    @Test
    void theFingerprintIsDeterministicAndSensitiveToEveryPartAndTheirOrder() {
        assertThat(IdempotencyService.fingerprint("a", "b", "c")).isEqualTo(IdempotencyService.fingerprint("a", "b", "c")).hasSize(64);
        assertThat(IdempotencyService.fingerprint("a", "b", "c")).isNotEqualTo(IdempotencyService.fingerprint("a", "b", "d"));
        assertThat(IdempotencyService.fingerprint("a", "b", "c")).isNotEqualTo(IdempotencyService.fingerprint("c", "b", "a"));
        assertThat(IdempotencyService.fingerprint("ab", "c")).as("part boundaries matter").isNotEqualTo(IdempotencyService.fingerprint("a", "bc"));
    }
}
