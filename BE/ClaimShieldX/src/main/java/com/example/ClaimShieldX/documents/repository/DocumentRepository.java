package com.example.ClaimShieldX.documents.repository;

import com.example.ClaimShieldX.documents.model.Document;
import com.example.ClaimShieldX.documents.model.DocumentStatus;
import com.example.ClaimShieldX.documents.model.DocumentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {
    // Always look documents up through their organization; a miss and a foreign document look the same.
    Optional<Document> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Optional<Document> findByIdAndOrganizationIdAndClaimId(UUID id, UUID organizationId, UUID claimId);

    Page<Document> findByOrganizationIdAndClaimId(UUID organizationId, UUID claimId, Pageable pageable);

    Optional<Document> findByClaimIdAndDocumentTypeAndSha256AndSupersededFalse(UUID claimId, DocumentType documentType, String sha256);

    long countByClaimId(UUID claimId);

    /** What the checklist needs from a document, without loading storage keys or file names. */
    interface EvidenceRow {
        UUID getId();

        UUID getClaimId();

        DocumentType getDocumentType();

        DocumentStatus getStatus();

        Instant getReceivedAt();
    }

    // One query for any number of claims, so a claim list does not query once per claim.
    @Query("""
            select d.id as id, d.claimId as claimId, d.documentType as documentType, d.status as status, d.receivedAt as receivedAt
            from Document d
            where d.organizationId = :organizationId and d.claimId in :claimIds and d.superseded = false
            """)
    List<EvidenceRow> findCurrentEvidence(@Param("organizationId") UUID organizationId, @Param("claimIds") Collection<UUID> claimIds);

    // Zero rows updated means it was already superseded (or does not exist).
    @Modifying
    @Query("update Document d set d.superseded = true, d.version = d.version + 1 where d.id = :id and d.superseded = false")
    int markSuperseded(@Param("id") UUID id);

    /**
     * Claims the document for extraction: only from a state that may be extracted, or from PROCESSING when that has gone on
     * for so long that the earlier run must have died. Zero rows updated means someone else holds it or it may not be
     * extracted (already VERIFIED).
     */
    @Modifying
    @Query("""
            update Document d set d.status = :processing, d.updatedAt = :now, d.version = d.version + 1
            where d.id = :id and d.organizationId = :organizationId
              and (d.status in :startable or (d.status = :processing and d.updatedAt < :staleBefore))
            """)
    int markProcessing(@Param("id") UUID id, @Param("organizationId") UUID organizationId,
                       @Param("processing") DocumentStatus processing, @Param("startable") Collection<DocumentStatus> startable,
                       @Param("now") Instant now, @Param("staleBefore") Instant staleBefore);

    /** Moves a document out of PROCESSING. Zero rows updated means it was no longer PROCESSING. */
    @Modifying
    @Query("update Document d set d.status = :to, d.updatedAt = :now, d.version = d.version + 1 where d.id = :id and d.status = :from")
    int transition(@Param("id") UUID id, @Param("from") DocumentStatus from, @Param("to") DocumentStatus to, @Param("now") Instant now);
}
