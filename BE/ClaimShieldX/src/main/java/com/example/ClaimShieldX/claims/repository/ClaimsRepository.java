package com.example.ClaimShieldX.claims.repository;

import com.example.ClaimShieldX.claims.model.Claim;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClaimsRepository extends JpaRepository<Claim, UUID> {
    boolean existsByOrganizationIdAndInternalId(UUID organizationId, String internalId);

    // Claims are always looked up through the caller's organization; another organization's claim looks like a miss.
    Optional<Claim> findByIdAndOrganizationId(UUID id, UUID organizationId);

    boolean existsByIdAndOrganizationId(UUID id, UUID organizationId);
}
