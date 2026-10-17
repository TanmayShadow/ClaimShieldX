package com.example.ClaimShieldX.idempotency;

import com.example.ClaimShieldX.idempotency.model.IdempotencyKey;
import com.example.ClaimShieldX.idempotency.model.IdempotencyState;
import com.example.ClaimShieldX.idempotency.repository.IdempotencyKeyRepository;
import com.example.ClaimShieldX.support.DbFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Not @Transactional on purpose: these verify committed database behaviour and clean up after themselves. */
@SpringBootTest
@ActiveProfiles("test")
class IdempotencyKeyConstraintTests {
    private static final String OPERATION = "POST /claims/{claimId}/documents";

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private IdempotencyKeyRepository repository;

    private DbFixtures fixtures;
    private UUID organization;

    @BeforeEach
    void setUp() {
        fixtures = new DbFixtures(jdbcTemplate);
        organization = fixtures.organization();
    }

    @AfterEach
    void cleanUp() {
        fixtures.cleanUp();
    }

    private void insert(UUID organizationId, String operation, String key, String fingerprint, String state,
                        Integer responseStatus, Instant completedAt) {
        jdbcTemplate.update("""
                insert into idempotency_keys(id, organization_id, operation, idempotency_key, request_fingerprint, state,
                                             response_status, completed_at, expires_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, now() + interval '24 hours')
                """, UUID.randomUUID(), organizationId, operation, key, fingerprint, state, responseStatus,
                completedAt == null ? null : java.sql.Timestamp.from(completedAt));
    }

    private void insertInProgress(UUID organizationId, String operation, String key) {
        insert(organizationId, operation, key, DbFixtures.sha256(), "IN_PROGRESS", null, null);
    }

    @Test
    void theSameKeyCannotBeUsedTwiceForTheSameOperationInAnOrganization() {
        insertInProgress(organization, OPERATION, "key-12345678");

        assertThatThrownBy(() -> insertInProgress(organization, OPERATION, "key-12345678"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_idempotency_scope");
    }

    @Test
    void theSameKeyIsAllowedForAnotherOperationOrAnotherOrganization() {
        insertInProgress(organization, OPERATION, "key-12345678");

        assertThatCode(() -> insertInProgress(organization, "POST /claims/{claimId}/investigations", "key-12345678")).doesNotThrowAnyException();
        assertThatCode(() -> insertInProgress(fixtures.organization(), OPERATION, "key-12345678")).doesNotThrowAnyException();
    }

    @Test
    void keyMustMatchTheDocumentedFormat() {
        for (String bad : new String[]{"short", "has space in it", "bad/char/12345", "bad;char;12345"}) {
            assertThatThrownBy(() -> insertInProgress(organization, OPERATION, bad))
                    .as(bad).isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_idempotency_key_format");
        }
        assertThatThrownBy(() -> insertInProgress(organization, OPERATION, "x".repeat(129)))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("value too long");
        assertThatCode(() -> insertInProgress(organization, OPERATION, "x".repeat(128))).doesNotThrowAnyException();
        assertThatCode(() -> insertInProgress(organization, OPERATION, "a1b2c3d4-e5f6.7890:ABC_xyz")).doesNotThrowAnyException();
    }

    @Test
    void fingerprintMustBeLowercaseSha256Hex() {
        assertThatThrownBy(() -> insert(organization, OPERATION, "key-12345678", "NOT-HEX", "IN_PROGRESS", null, null))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_idempotency_fingerprint");
    }

    @Test
    void aCompletedKeyMustCarryItsOutcomeAndAnOpenKeyMustNot() {
        assertThatThrownBy(() -> insert(organization, OPERATION, "key-aaaaaaaa", DbFixtures.sha256(), "COMPLETED", null, null))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_idempotency_completed");
        assertThatThrownBy(() -> insert(organization, OPERATION, "key-bbbbbbbb", DbFixtures.sha256(), "IN_PROGRESS", 201, Instant.now()))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_idempotency_completed");
        assertThatCode(() -> insert(organization, OPERATION, "key-cccccccc", DbFixtures.sha256(), "COMPLETED", 201, Instant.now()))
                .doesNotThrowAnyException();
    }

    @Test
    void stateMustBeAKnownValue() {
        assertThatThrownBy(() -> insert(organization, OPERATION, "key-12345678", DbFixtures.sha256(), "DONE", null, null))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_idempotency_state");
    }

    @Test
    void entityMappingRoundTripsAndIsFoundByItsScope() {
        IdempotencyKey key = new IdempotencyKey();
        key.setOrganizationId(organization);
        key.setOperation(OPERATION);
        key.setIdempotencyKey("key-roundtrip-1");
        key.setRequestFingerprint(DbFixtures.sha256());
        key.setExpiresAt(Instant.now().plus(24, ChronoUnit.HOURS));
        repository.saveAndFlush(key);

        IdempotencyKey found = repository.findByOrganizationIdAndOperationAndIdempotencyKey(organization, OPERATION, "key-roundtrip-1").orElseThrow();
        assertThat(found.getState()).isEqualTo(IdempotencyState.IN_PROGRESS);
        assertThat(found.getResponseStatus()).isNull();

        UUID resourceId = UUID.randomUUID();
        found.setState(IdempotencyState.COMPLETED);
        found.setResponseStatus(201);
        found.setResourceType("DOCUMENT");
        found.setResourceId(resourceId);
        found.setCompletedAt(Instant.now());
        repository.saveAndFlush(found);

        IdempotencyKey completed = repository.findById(found.getId()).orElseThrow();
        assertThat(completed.getState()).isEqualTo(IdempotencyState.COMPLETED);
        assertThat(completed.getResourceId()).isEqualTo(resourceId);
        assertThat(repository.findByOrganizationIdAndOperationAndIdempotencyKey(fixtures.organization(), OPERATION, "key-roundtrip-1")).isEmpty();
    }
}
