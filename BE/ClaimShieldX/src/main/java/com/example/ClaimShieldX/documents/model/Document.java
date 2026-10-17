package com.example.ClaimShieldX.documents.model;

import com.example.ClaimShieldX.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "documents")
@Getter
@Setter
public class Document extends BaseEntity {
    @Setter(AccessLevel.NONE)
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "claim_id", nullable = false, updatable = false)
    private UUID claimId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, updatable = false)
    private DocumentType documentType;

    @Column(name = "file_name", nullable = false, updatable = false)
    private String fileName;

    @Column(name = "content_type", nullable = false, updatable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @Column(name = "sha256", nullable = false, updatable = false)
    private String sha256;

    @Column(name = "object_key", nullable = false, updatable = false)
    private String objectKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private DocumentStatus status = DocumentStatus.RECEIVED;

    @Column(name = "external_document_id", updatable = false)
    private String externalDocumentId;

    @Column(name = "supersedes_document_id", updatable = false)
    private UUID supersedesDocumentId;

    @Column(name = "superseded", nullable = false)
    private boolean superseded;

    @Column(name = "uploaded_by", nullable = false, updatable = false)
    private UUID uploadedBy;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt = Instant.now();
}
