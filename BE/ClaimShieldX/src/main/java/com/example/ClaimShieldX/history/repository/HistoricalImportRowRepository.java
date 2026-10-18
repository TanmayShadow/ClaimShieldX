package com.example.ClaimShieldX.history.repository;

import com.example.ClaimShieldX.history.model.HistoricalImportRow;
import com.example.ClaimShieldX.history.model.RowStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface HistoricalImportRowRepository extends JpaRepository<HistoricalImportRow, UUID> {
    Page<HistoricalImportRow> findByImportIdAndOrganizationId(UUID importId, UUID organizationId, Pageable pageable);

    Page<HistoricalImportRow> findByImportIdAndOrganizationIdAndStatus(UUID importId, UUID organizationId, RowStatus status, Pageable pageable);
}
