package com.example.ClaimShieldX.claims.repository;

import com.example.ClaimShieldX.claims.model.Claim;
import com.example.ClaimShieldX.claims.model.ClaimStage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClaimsRepository extends JpaRepository<Claim, UUID> {
    boolean existsByOrganizationIdAndInternalId(UUID organizationId, String internalId);

    // Claims are always looked up through the caller's organization; another organization's claim looks like a miss.
    Optional<Claim> findByIdAndOrganizationId(UUID id, UUID organizationId);

    boolean existsByIdAndOrganizationId(UUID id, UUID organizationId);

    // Explicit queries: the property is named isActive, which a derived query name would parse ambiguously.
    @Query("select c from Claim c where c.organizationId = :organizationId and c.isActive = :active")
    Page<Claim> findPage(@Param("organizationId") UUID organizationId, @Param("active") boolean active, Pageable pageable);

    @Query("select c from Claim c where c.organizationId = :organizationId and c.isActive = :active and c.currentStage = :stage")
    Page<Claim> findPageByStage(@Param("organizationId") UUID organizationId, @Param("active") boolean active,
                                @Param("stage") ClaimStage stage, Pageable pageable);
}
