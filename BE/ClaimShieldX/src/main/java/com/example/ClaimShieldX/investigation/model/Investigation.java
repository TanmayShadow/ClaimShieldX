package com.example.ClaimShieldX.investigation.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * An investigation and, at the same time, its queue entry. JPA only inserts and reads it: every change after creation is a
 * guarded SQL update (see {@code InvestigationStore}), because the changes are races between workers that a plain entity save
 * would lose.
 */
@Entity
@Table(name = "investigations")
@Getter
@Setter
public class Investigation {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, updatable = false)
    private Instant updatedAt;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "claim_id", nullable = false, updatable = false)
    private UUID claimId;

    @Column(name = "claim_version", nullable = false, updatable = false)
    private long claimVersion;

    @Column(name = "requested_by", nullable = false, updatable = false)
    private UUID requestedBy;

    @Column(name = "note", updatable = false)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, updatable = false)
    private InvestigationStatus status;

    @Column(name = "attempt", nullable = false, updatable = false)
    private int attempt;

    @Column(name = "max_attempts", nullable = false, updatable = false)
    private int maxAttempts;

    @Column(name = "next_attempt_at", updatable = false)
    private Instant nextAttemptAt;

    @Column(name = "lease_owner", updatable = false)
    private String leaseOwner;

    @Column(name = "lease_expires_at", updatable = false)
    private Instant leaseExpiresAt;

    @Column(name = "cancel_requested", nullable = false, updatable = false)
    private boolean cancelRequested;

    @Column(name = "started_at", updatable = false)
    private Instant startedAt;

    @Column(name = "finished_at", updatable = false)
    private Instant finishedAt;

    @Column(name = "error_code", updatable = false)
    private String errorCode;

    @Column(name = "error_message", updatable = false)
    private String errorMessage;

    @Column(name = "rule_run_id", updatable = false)
    private UUID ruleRunId;
}
