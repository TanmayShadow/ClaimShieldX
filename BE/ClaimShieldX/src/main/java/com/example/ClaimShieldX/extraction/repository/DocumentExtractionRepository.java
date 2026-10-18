package com.example.ClaimShieldX.extraction.repository;

import com.example.ClaimShieldX.extraction.model.DocumentExtraction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentExtractionRepository extends JpaRepository<DocumentExtraction, UUID> {
    // Always through the organization: another organization's extraction looks like a miss.
    Optional<DocumentExtraction> findFirstByOrganizationIdAndDocumentIdOrderByAttemptDesc(UUID organizationId, UUID documentId);

    long countByDocumentId(UUID documentId);

    /** The newest attempt of each given document (successful or not), in one query. */
    @Query("""
            select e from DocumentExtraction e
            where e.organizationId = :organizationId and e.documentId in :documentIds
              and e.attempt = (select max(x.attempt) from DocumentExtraction x where x.documentId = e.documentId)
            """)
    List<DocumentExtraction> findNewestByDocuments(@Param("organizationId") UUID organizationId, @Param("documentIds") Collection<UUID> documentIds);

    /**
     * Ids of the newest successful extractions of final invoices whose invoice number, reduced to ASCII letters and digits in lower
     * case, equals {@code key} (the same reduction as {@code InvoiceKey}, which re-checks every row it receives). The invoice of
     * {@code excludeDocumentId} is left out.
     */
    @Query(value = """
            select e.id from document_extractions e
            where e.organization_id = :organizationId and e.document_type = 'FINAL_INVOICE' and e.status = 'SUCCEEDED'
              and e.document_id <> :excludeDocumentId
              and e.attempt = (select max(x.attempt) from document_extractions x where x.document_id = e.document_id)
              and exists (select 1 from jsonb_array_elements(e.result -> 'fields') f
                          where f ->> 'name' = 'documentNumber'
                            and lower(regexp_replace(coalesce(f ->> 'normalizedValue', ''), '[^A-Za-z0-9]', '', 'g')) = :key)
            order by e.created_at desc, e.id
            limit :limit
            """, nativeQuery = true)
    List<UUID> findInvoiceIdsByNumberKey(@Param("organizationId") UUID organizationId, @Param("key") String key,
                                         @Param("excludeDocumentId") UUID excludeDocumentId, @Param("limit") int limit);

    @Query("select coalesce(max(e.attempt), 0) from DocumentExtraction e where e.documentId = :documentId")
    int lastAttempt(@Param("documentId") UUID documentId);
}
