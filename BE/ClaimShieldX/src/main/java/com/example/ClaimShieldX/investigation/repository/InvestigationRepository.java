package com.example.ClaimShieldX.investigation.repository;

import com.example.ClaimShieldX.investigation.model.Investigation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvestigationRepository extends JpaRepository<Investigation, UUID> {
    // Always through the organization: another organization's investigation looks like a miss.
    Optional<Investigation> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Page<Investigation> findByOrganizationIdAndClaimId(UUID organizationId, UUID claimId, Pageable pageable);

    @Query("select i from Investigation i where i.organizationId = :organizationId and i.claimId = :claimId "
            + "and i.status not in (com.example.ClaimShieldX.investigation.model.InvestigationStatus.COMPLETED, "
            + "com.example.ClaimShieldX.investigation.model.InvestigationStatus.FAILED, com.example.ClaimShieldX.investigation.model.InvestigationStatus.CANCELLED)")
    Optional<Investigation> findActive(@Param("organizationId") UUID organizationId, @Param("claimId") UUID claimId);

    long countByOrganizationIdAndCreatedAtGreaterThanEqual(UUID organizationId, Instant since);
}
