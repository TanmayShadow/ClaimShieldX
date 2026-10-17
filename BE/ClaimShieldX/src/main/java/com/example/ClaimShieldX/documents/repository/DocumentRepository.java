package com.example.ClaimShieldX.documents.repository;

import com.example.ClaimShieldX.documents.model.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {
    // Always look documents up through their organization; a miss and a foreign document look the same.
    Optional<Document> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
