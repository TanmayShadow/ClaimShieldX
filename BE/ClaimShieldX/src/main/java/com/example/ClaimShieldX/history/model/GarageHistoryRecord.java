package com.example.ClaimShieldX.history.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Aggregate figures an insurer reports for one garage and period. Context for a person, never a verdict about a garage. */
@Entity
@Table(name = "garage_history_records")
@Getter
@Setter
public class GarageHistoryRecord {
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

    @Column(name = "garage_id", nullable = false, updatable = false)
    private String garageId;

    @Column(name = "reporting_period_start", nullable = false, updatable = false)
    private LocalDate reportingPeriodStart;

    @Column(name = "reporting_period_end", nullable = false, updatable = false)
    private LocalDate reportingPeriodEnd;

    @Column(name = "claim_count", nullable = false, updatable = false)
    private int claimCount;

    @Column(name = "claimed_total", updatable = false)
    private BigDecimal claimedTotal;

    @Column(name = "approved_total", updatable = false)
    private BigDecimal approvedTotal;

    @Column(name = "currency", nullable = false, updatable = false)
    private String currency;

    @Column(name = "estimate_invoice_variance_count", updatable = false)
    private Integer estimateInvoiceVarianceCount;

    @Column(name = "disputed_or_reopened_count", updatable = false)
    private Integer disputedOrReopenedCount;

    @Column(name = "confirmed_outcome_count", updatable = false)
    private Integer confirmedOutcomeCount;

    @Column(name = "content_hash", nullable = false, updatable = false)
    private String contentHash;
}
