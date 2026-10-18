package com.example.ClaimShieldX.rules.repository;

import com.example.ClaimShieldX.rules.model.RuleResultRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface RuleResultRepository extends JpaRepository<RuleResultRow, UUID> {
    List<RuleResultRow> findByOrganizationIdAndRunId(UUID organizationId, UUID runId);

    /** One query for many runs, so a list of runs does not query once per run. */
    List<RuleResultRow> findByOrganizationIdAndRunIdIn(UUID organizationId, Collection<UUID> runIds);
}
