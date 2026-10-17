package com.example.ClaimShieldX.idempotency.service;

import com.example.ClaimShieldX.common.utils.TokenHasher;
import com.example.ClaimShieldX.exceptions.IdempotencyKeyReusedException;
import com.example.ClaimShieldX.exceptions.RequestInProgressException;
import com.example.ClaimShieldX.exceptions.RequestValidationException;
import com.example.ClaimShieldX.idempotency.model.IdempotencyKey;
import com.example.ClaimShieldX.idempotency.model.IdempotencyState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Implements the Idempotency-Key rules from the API contract. A key is scoped to (organization, operation).
 * Only successful requests are remembered: a failed attempt releases its key, so the client may retry.
 */
@Slf4j
@Service
public class IdempotencyService {
    private static final Pattern KEY_FORMAT = Pattern.compile("^[A-Za-z0-9._:-]{8,128}$");

    public enum Mode {NEW, REPLAY}

    public record Begin(Mode mode, UUID keyId, String resourceType, UUID resourceId) {
    }

    private final IdempotencyStore store;
    private final Duration ttl;
    private final Duration inProgressTimeout;

    public IdempotencyService(IdempotencyStore store,
                              @Value("${app.idempotency.ttl-hours:24}") long ttlHours,
                              @Value("${app.idempotency.in-progress-timeout-seconds:300}") long inProgressTimeoutSeconds) {
        this.store = store;
        this.ttl = Duration.ofHours(ttlHours);
        this.inProgressTimeout = Duration.ofSeconds(inProgressTimeoutSeconds);
    }

    public static void validateKey(String key) {
        if (key == null || !KEY_FORMAT.matcher(key).matches()) {
            throw RequestValidationException.of("Idempotency-Key", "must be 8 to 128 characters from A-Z a-z 0-9 . _ : -");
        }
    }

    public static String fingerprint(String... parts) {
        return TokenHasher.sha256Hex(String.join("\n", parts));
    }

    public Begin begin(UUID organizationId, String operation, String key, String fingerprint) {
        IdempotencyKey existing = store.find(organizationId, operation, key).orElse(null);
        if (existing == null) {
            IdempotencyKey inserted = tryInsert(organizationId, operation, key, fingerprint);
            if (inserted != null) {
                return new Begin(Mode.NEW, inserted.getId(), null, null);
            }
            // Lost the race to a concurrent request with the same key; judge that request's record instead.
            existing = store.find(organizationId, operation, key).orElseThrow(RequestInProgressException::new);
        }
        return judge(existing, organizationId, operation, key, fingerprint);
    }

    private Begin judge(IdempotencyKey existing, UUID organizationId, String operation, String key, String fingerprint) {
        Instant now = Instant.now();
        if (existing.getExpiresAt().isBefore(now)) {
            return replaceAndStart(existing, organizationId, operation, key, fingerprint);
        }
        if (!existing.getRequestFingerprint().equals(fingerprint)) {
            throw new IdempotencyKeyReusedException();
        }
        if (existing.getState() == IdempotencyState.COMPLETED) {
            return new Begin(Mode.REPLAY, existing.getId(), existing.getResourceType(), existing.getResourceId());
        }
        // Still marked in progress. A request that died mid-way must not block the client for the whole TTL.
        if (existing.getCreatedAt().isBefore(now.minus(inProgressTimeout))) {
            log.warn("Taking over a stale idempotency key (organization={}, operation={})", organizationId, operation);
            return replaceAndStart(existing, organizationId, operation, key, fingerprint);
        }
        throw new RequestInProgressException();
    }

    private Begin replaceAndStart(IdempotencyKey stale, UUID organizationId, String operation, String key, String fingerprint) {
        store.delete(stale.getId());
        IdempotencyKey inserted = tryInsert(organizationId, operation, key, fingerprint);
        if (inserted == null) {
            throw new RequestInProgressException();
        }
        return new Begin(Mode.NEW, inserted.getId(), null, null);
    }

    /** Null when a concurrent request inserted the same key first. */
    private IdempotencyKey tryInsert(UUID organizationId, String operation, String key, String fingerprint) {
        try {
            return store.insert(organizationId, operation, key, fingerprint, Instant.now().plus(ttl));
        } catch (DataIntegrityViolationException e) {
            return null;
        }
    }

    public void complete(UUID keyId, int responseStatus, String resourceType, UUID resourceId) {
        store.complete(keyId, responseStatus, resourceType, resourceId);
    }

    /** Called when the request failed, so the client can retry with the same key. Never throws. */
    public void release(UUID keyId) {
        try {
            store.delete(keyId);
        } catch (RuntimeException e) {
            log.warn("Could not release an idempotency key; it will expire on its own", e);
        }
    }
}
