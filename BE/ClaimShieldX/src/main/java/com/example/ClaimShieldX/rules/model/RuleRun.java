package com.example.ClaimShieldX.rules.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** One evaluation of every rule for one claim. Rows are only ever inserted, never changed. */
@Entity
@Table(name = "rule_runs")
@Getter
@Setter
public class RuleRun {
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

    @Column(name = "requested_by", updatable = false)
    private UUID requestedBy;

    @Column(name = "rule_set_version", nullable = false, updatable = false)
    private String ruleSetVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config", nullable = false, updatable = false)
    private Map<String, Object> config;

    @Column(name = "config_sha256", nullable = false, updatable = false)
    private String configSha256;

    @Column(name = "input_fingerprint", nullable = false, updatable = false)
    private String inputFingerprint;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "inputs", nullable = false, updatable = false)
    private Map<String, Object> inputs;

    @Column(name = "pass_count", nullable = false, updatable = false)
    private int passCount;

    @Column(name = "flag_count", nullable = false, updatable = false)
    private int flagCount;

    @Column(name = "incomplete_count", nullable = false, updatable = false)
    private int incompleteCount;

    @Column(name = "not_applicable_count", nullable = false, updatable = false)
    private int notApplicableCount;

    @Column(name = "error_count", nullable = false, updatable = false)
    private int errorCount;
}
