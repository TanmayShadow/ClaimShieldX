package com.example.ClaimShieldX.history.repository;

import com.example.ClaimShieldX.history.model.HistoricalImport;
import com.example.ClaimShieldX.history.model.ImportFailureCode;
import com.example.ClaimShieldX.history.model.ImportStatus;
import com.example.ClaimShieldX.history.model.ImportType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HistoricalImportRepository extends JpaRepository<HistoricalImport, UUID> {
    // Always through the organization: another organization's import looks like a miss.
    Optional<HistoricalImport> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Page<HistoricalImport> findByOrganizationId(UUID organizationId, Pageable pageable);

    Optional<HistoricalImport> findFirstByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);

    /** Marks imports that were left PROCESSING by a process that died. Zero rows means there were none. */
    @Modifying
    @Query("""
            update HistoricalImport i set i.status = :failed, i.failureCode = :code, i.failureDetail = :detail, i.finishedAt = :now
            where i.organizationId = :organizationId and i.importType = :type and i.status = :processing and i.startedAt < :before
            """)
    int failStale(@Param("organizationId") UUID organizationId, @Param("type") ImportType type, @Param("processing") ImportStatus processing,
                  @Param("failed") ImportStatus failed, @Param("code") ImportFailureCode code, @Param("detail") String detail,
                  @Param("now") Instant now, @Param("before") Instant before);

    Optional<HistoricalImport> findFirstByOrganizationIdAndImportTypeOrderByCreatedAtDesc(UUID organizationId, ImportType importType);
}
