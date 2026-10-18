package com.example.ClaimShieldX.history.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/** The record of one import: who uploaded which file, what became of it, and how many rows ended up where. */
@Entity
@Table(name = "historical_imports")
@Getter
@Setter
public class HistoricalImport {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "uploaded_by", nullable = false, updatable = false)
    private UUID uploadedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "import_type", nullable = false, updatable = false)
    private ImportType importType;

    @Column(name = "template_version", nullable = false, updatable = false)
    private String templateVersion;

    @Column(name = "source", updatable = false)
    private String source;

    @Column(name = "sha256", nullable = false, updatable = false)
    private String sha256;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ImportStatus status = ImportStatus.RECEIVED;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_code")
    private ImportFailureCode failureCode;

    @Column(name = "failure_detail")
    private String failureDetail;

    @Column(name = "total_rows", nullable = false)
    private int totalRows;

    @Column(name = "accepted_rows", nullable = false)
    private int acceptedRows;

    @Column(name = "rejected_rows", nullable = false)
    private int rejectedRows;

    @Column(name = "skipped_duplicate_rows", nullable = false)
    private int skippedDuplicateRows;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;
}
