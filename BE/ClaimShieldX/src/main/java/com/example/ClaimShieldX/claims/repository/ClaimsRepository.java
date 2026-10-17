package com.example.ClaimShieldX.claims.repository;

import com.example.ClaimShieldX.claims.model.Claim;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ClaimsRepository extends JpaRepository<Claim, UUID> {
    boolean existsByOrganizationIdAndInternalId(UUID organizationId, String internalId);
}
