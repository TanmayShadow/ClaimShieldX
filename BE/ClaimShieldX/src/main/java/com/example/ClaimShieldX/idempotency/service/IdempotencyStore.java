package com.example.ClaimShieldX.idempotency.service;

import com.example.ClaimShieldX.idempotency.model.IdempotencyKey;
import com.example.ClaimShieldX.idempotency.model.IdempotencyState;
import com.example.ClaimShieldX.idempotency.repository.IdempotencyKeyRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Each method runs in its own transaction and commits immediately, so an "in progress" marker is visible to
 * concurrent requests before the real work starts. Kept separate from the service so the transaction
 * boundaries are real (calls between methods of one class would bypass them).
 */
@Component
class IdempotencyStore {
    private final IdempotencyKeyRepository repository;

    IdempotencyStore(IdempotencyKeyRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    Optional<IdempotencyKey> find(UUID organizationId, String operation, String key) {
        return repository.findByOrganizationIdAndOperationAndIdempotencyKey(organizationId, operation, key);
    }

    /**
     * Throws DataIntegrityViolationException when another request inserted the same key first. It must be thrown,
     * not caught here: once the exception passed through the repository the transaction is rollback-only, so a
     * caught exception would turn into an UnexpectedRollbackException on commit. The caller handles it.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    IdempotencyKey insert(UUID organizationId, String operation, String key, String fingerprint, Instant expiresAt) {
        IdempotencyKey record = new IdempotencyKey();
        record.setOrganizationId(organizationId);
        record.setOperation(operation);
        record.setIdempotencyKey(key);
        record.setRequestFingerprint(fingerprint);
        record.setExpiresAt(expiresAt);
        return repository.saveAndFlush(record);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void complete(UUID id, int responseStatus, String resourceType, UUID resourceId) {
        IdempotencyKey record = repository.findById(id).orElseThrow();
        record.setState(IdempotencyState.COMPLETED);
        record.setResponseStatus(responseStatus);
        record.setResourceType(resourceType);
        record.setResourceId(resourceId);
        record.setCompletedAt(Instant.now());
        repository.saveAndFlush(record);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void delete(UUID id) {
        repository.deleteById(id);
    }
}
