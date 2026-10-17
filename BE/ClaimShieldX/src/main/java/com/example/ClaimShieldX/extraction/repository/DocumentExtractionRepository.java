package com.example.ClaimShieldX.extraction.repository;

import com.example.ClaimShieldX.extraction.model.DocumentExtraction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentExtractionRepository extends JpaRepository<DocumentExtraction, UUID> {
    // Always through the organization: another organization's extraction looks like a miss.
    Optional<DocumentExtraction> findFirstByOrganizationIdAndDocumentIdOrderByAttemptDesc(UUID organizationId, UUID documentId);

    long countByDocumentId(UUID documentId);

    @Query("select coalesce(max(e.attempt), 0) from DocumentExtraction e where e.documentId = :documentId")
    int lastAttempt(@Param("documentId") UUID documentId);
}
