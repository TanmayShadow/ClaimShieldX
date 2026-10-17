package com.example.ClaimShieldX.documents.repository;

import com.example.ClaimShieldX.documents.model.Document;
import com.example.ClaimShieldX.documents.model.DocumentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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

    // Zero rows updated means it was already superseded (or does not exist).
    @Modifying
    @Query("update Document d set d.superseded = true, d.version = d.version + 1 where d.id = :id and d.superseded = false")
    int markSuperseded(@Param("id") UUID id);
}
