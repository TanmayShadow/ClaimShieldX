package com.example.ClaimShieldX.history.repository;

import com.example.ClaimShieldX.history.model.HistoricalClaim;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Every query names the organization: history is never read across organizations. */
@Repository
public interface HistoricalClaimRepository extends JpaRepository<HistoricalClaim, UUID> {
    long countByOrganizationId(UUID organizationId);

    long countByOrganizationIdAndGarageId(UUID organizationId, String garageId);

    @Query("select c from HistoricalClaim c where c.organizationId = :organizationId and c.sourceRecordId in :ids")
    List<HistoricalClaim> findBySourceRecordIds(@Param("organizationId") UUID organizationId, @Param("ids") Collection<String> ids);

    @Query("select c from HistoricalClaim c where c.organizationId = :organizationId and c.externalClaimId in :ids")
    List<HistoricalClaim> findByExternalClaimIds(@Param("organizationId") UUID organizationId, @Param("ids") Collection<String> ids);

    @Query("select c from HistoricalClaim c where c.organizationId = :organizationId and c.vehicleToken = :token order by c.incidentAt desc, c.id")
    List<HistoricalClaim> findByVehicle(@Param("organizationId") UUID organizationId, @Param("token") String token, Pageable pageable);

    @Query("select c from HistoricalClaim c where c.organizationId = :organizationId and c.policyToken = :token order by c.incidentAt desc, c.id")
    List<HistoricalClaim> findByPolicy(@Param("organizationId") UUID organizationId, @Param("token") String token, Pageable pageable);

    @Query("select c from HistoricalClaim c where c.organizationId = :organizationId and c.claimantToken = :token order by c.incidentAt desc, c.id")
    List<HistoricalClaim> findByClaimant(@Param("organizationId") UUID organizationId, @Param("token") String token, Pageable pageable);
}
