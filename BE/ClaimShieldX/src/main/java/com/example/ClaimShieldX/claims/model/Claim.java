package com.example.ClaimShieldX.claims.model;

import com.example.ClaimShieldX.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

//      Group	Recommended fields
//      Internal identity	id, organization_id
//        External reference	external_claim_id, source_system -> not required right now
//        Versioning	claim_version, current_stage
//        Policy	policy_token, policy_type, policy_start_date, policy_end_date
//        Vehicle	vehicle_token, permitted registration reference, make/model
//        Claimant	claimant_token, only when necessary and authorized
//         Garage	garage_id or a pseudonymous garage reference
//        Incident	incident_at, reported_at, incident type, permitted location precision
//        Financial	initial_estimate_amount, approved_estimate_amount, currency, deductible where relevant
//        Workflow	status, created_by, created_at, updated_at

@Entity
@Table(name="claims")
@Getter
@Setter
public class Claim extends BaseEntity {
    @Column(name="internal_id", nullable = false)
    private String internalId;

    @Column(name="organization_id")
    private UUID organizationId;

    @Column(name="claim_version")
    private long claimVersion;

    @Enumerated(EnumType.STRING)
    @Column(name="current_stage")
    private ClaimStage currentStage;

    @Embedded
    private Policy policy;

    @Embedded
    private Claimant claimant;

    @Embedded
    private Garage garage;

    @Embedded
    private Vehicle vehicle;

    @Embedded
    private Incident incident;

    @Embedded
    private Financial financial;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "createdAt", column = @Column(name = "workflow_created_at")),
            @AttributeOverride(name = "updatedAt", column = @Column(name = "workflow_updated_at"))
    })
    private Workflow workflow;

    @Column(name="is_active")
    private Boolean isActive=true;

    @Column(name="version")
    private Integer version=1;

}
