package com.example.ClaimShieldX.extraction.model;

import com.example.ClaimShieldX.documents.model.DocumentType;
import com.example.ClaimShieldX.extraction.schema.ExtractionResult;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/** One extraction attempt on a document. Rows are only ever inserted, never changed. */
@Entity
@Table(name = "document_extractions")
@Getter
@Setter
public class DocumentExtraction {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "claim_id", nullable = false, updatable = false)
    private UUID claimId;

    @Column(name = "document_id", nullable = false, updatable = false)
    private UUID documentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, updatable = false)
    private DocumentType documentType;

    @Column(name = "attempt", nullable = false, updatable = false)
    private int attempt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, updatable = false)
    private ExtractionRunStatus status;

    @Column(name = "schema_version", nullable = false, updatable = false)
    private String schemaVersion;

    @Column(name = "prompt_version", nullable = false, updatable = false)
    private String promptVersion;

    @Column(name = "model_id", nullable = false, updatable = false)
    private String modelId;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_code", updatable = false)
    private ExtractionFailureCode failureCode;

    @Column(name = "page_count", updatable = false)
    private Integer pageCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result", updatable = false)
    private ExtractionResult result;

    @Column(name = "model_calls", nullable = false, updatable = false)
    private int modelCalls;

    @Column(name = "input_tokens", updatable = false)
    private Integer inputTokens;

    @Column(name = "output_tokens", updatable = false)
    private Integer outputTokens;

    @Column(name = "duration_ms", nullable = false, updatable = false)
    private long durationMs;
}
