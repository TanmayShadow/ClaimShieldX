package com.example.ClaimShieldX.idempotency.repository;

import com.example.ClaimShieldX.idempotency.model.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, UUID> {
    Optional<IdempotencyKey> findByOrganizationIdAndOperationAndIdempotencyKey(UUID organizationId, String operation, String idempotencyKey);
}
