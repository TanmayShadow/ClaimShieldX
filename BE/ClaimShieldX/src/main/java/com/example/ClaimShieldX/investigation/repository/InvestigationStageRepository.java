package com.example.ClaimShieldX.investigation.repository;

import com.example.ClaimShieldX.investigation.model.InvestigationStageRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface InvestigationStageRepository extends JpaRepository<InvestigationStageRow, UUID> {
    List<InvestigationStageRow> findByOrganizationIdAndInvestigationId(UUID organizationId, UUID investigationId);

    /** One query for many investigations, so a list does not query once per row. */
    List<InvestigationStageRow> findByOrganizationIdAndInvestigationIdIn(UUID organizationId, Collection<UUID> investigationIds);
}
