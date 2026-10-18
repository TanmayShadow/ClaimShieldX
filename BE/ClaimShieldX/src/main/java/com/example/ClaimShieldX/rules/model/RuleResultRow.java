package com.example.ClaimShieldX.rules.model;

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
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The stored form of a {@link RuleResult}. Never changed after it is inserted. */
@Entity
@Table(name = "rule_results")
@Getter
@Setter
public class RuleResultRow {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "claim_id", nullable = false, updatable = false)
    private UUID claimId;

    @Column(name = "run_id", nullable = false, updatable = false)
    private UUID runId;

    @Column(name = "rule_id", nullable = false, updatable = false)
    private String ruleId;

    @Column(name = "rule_version", nullable = false, updatable = false)
    private String ruleVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, updatable = false)
    private RuleOutcome outcome;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", updatable = false)
    private RuleSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "reliability", nullable = false, updatable = false)
    private InputReliability reliability;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reliability_reasons", nullable = false, updatable = false)
    private List<String> reliabilityReasons;

    @Column(name = "explanation", nullable = false, updatable = false)
    private String explanation;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_values", nullable = false, updatable = false)
    private Map<String, Object> values;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence", nullable = false, updatable = false)
    private List<EvidenceRef> evidence;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "considerations", nullable = false, updatable = false)
    private List<String> considerations;

    @Column(name = "executed_at", nullable = false, updatable = false)
    private Instant executedAt;
}
