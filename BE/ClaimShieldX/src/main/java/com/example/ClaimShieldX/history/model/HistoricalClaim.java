package com.example.ClaimShieldX.history.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A claim from the insurer's history, as imported. Immutable: a changed record is rejected on import, never overwritten. */
@Entity
@Table(name = "historical_claims")
@Getter
@Setter
public class HistoricalClaim {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "import_id", nullable = false, updatable = false)
    private UUID importId;

    @Column(name = "row_number", nullable = false, updatable = false)
    private int rowNumber;

    @Column(name = "source_record_id", nullable = false, updatable = false)
    private String sourceRecordId;

    @Column(name = "external_claim_id", nullable = false, updatable = false)
    private String externalClaimId;

    @Column(name = "claim_version", nullable = false, updatable = false)
    private int claimVersion;

    @Column(name = "vehicle_token", nullable = false, updatable = false)
    private String vehicleToken;

    @Column(name = "policy_token", updatable = false)
    private String policyToken;

    @Column(name = "claimant_token", updatable = false)
    private String claimantToken;

    @Column(name = "garage_id", updatable = false)
    private String garageId;

    @Column(name = "incident_at", nullable = false, updatable = false)
    private Instant incidentAt;

    @Column(name = "incident_at_precision", nullable = false, updatable = false)
    private String incidentAtPrecision;

    @Column(name = "reported_at", updatable = false)
    private Instant reportedAt;

    @Column(name = "claim_type", nullable = false, updatable = false)
    private String claimType;

    @Column(name = "claim_status", nullable = false, updatable = false)
    private String claimStatus;

    @Column(name = "outcome_status", updatable = false)
    private String outcomeStatus;

    @Column(name = "claimed_amount", updatable = false)
    private BigDecimal claimedAmount;

    @Column(name = "approved_amount", updatable = false)
    private BigDecimal approvedAmount;

    @Column(name = "currency", nullable = false, updatable = false)
    private String currency;

    @Column(name = "repair_summary", updatable = false)
    private String repairSummary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "damage_area_codes", nullable = false, updatable = false)
    private List<String> damageAreaCodes = List.of();

    @Column(name = "repair_completed_at", updatable = false)
    private Instant repairCompletedAt;

    @Column(name = "content_hash", nullable = false, updatable = false)
    private String contentHash;
}
