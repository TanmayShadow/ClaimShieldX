package com.example.ClaimShieldX.investigation.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** What one stage of one investigation did. Written only by the worker, with guarded SQL; read for the status response. */
@Entity
@Table(name = "investigation_stages")
@Getter
@Setter
public class InvestigationStageRow {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "investigation_id", nullable = false, updatable = false)
    private UUID investigationId;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "claim_id", nullable = false, updatable = false)
    private UUID claimId;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage", nullable = false, updatable = false)
    private Stage stage;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, updatable = false)
    private StageStatus status;

    @Column(name = "attempts", nullable = false, updatable = false)
    private int attempts;

    @Column(name = "started_at", updatable = false)
    private Instant startedAt;

    @Column(name = "finished_at", updatable = false)
    private Instant finishedAt;

    @Column(name = "updated_at", nullable = false, updatable = false)
    private Instant updatedAt;

    @Column(name = "error_code", updatable = false)
    private String errorCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "summary", nullable = false, updatable = false)
    private Map<String, Object> summary;
}
