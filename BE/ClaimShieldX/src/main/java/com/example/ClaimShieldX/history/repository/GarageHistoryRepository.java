package com.example.ClaimShieldX.history.repository;

import com.example.ClaimShieldX.history.model.GarageHistoryRecord;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface GarageHistoryRepository extends JpaRepository<GarageHistoryRecord, UUID> {
    long countByOrganizationId(UUID organizationId);

    @Query("select g from GarageHistoryRecord g where g.organizationId = :organizationId and g.sourceRecordId in :ids")
    List<GarageHistoryRecord> findBySourceRecordIds(@Param("organizationId") UUID organizationId, @Param("ids") Collection<String> ids);

    @Query("select g from GarageHistoryRecord g where g.organizationId = :organizationId and g.garageId in :garageIds")
    List<GarageHistoryRecord> findByGarageIds(@Param("organizationId") UUID organizationId, @Param("garageIds") Collection<String> garageIds);

    @Query("select g from GarageHistoryRecord g where g.organizationId = :organizationId and g.garageId = :garageId order by g.reportingPeriodStart desc, g.id")
    List<GarageHistoryRecord> findByGarage(@Param("organizationId") UUID organizationId, @Param("garageId") String garageId, Pageable pageable);
}
