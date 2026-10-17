-- Flyway Migration Script: Create Claims Table
CREATE TABLE claims (
    -- Inherited Primary Key from BaseEntity
                        id UUID PRIMARY KEY,

    -- Inherited System Auditing from BaseEntity
                        created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Root Claim Entity Fields
                        internal_id VARCHAR(255),
                        organization_id UUID NOT NULL,
                        claim_version BIGINT NOT NULL DEFAULT 1,
                        current_stage VARCHAR(100),
                        is_active BOOLEAN NOT NULL DEFAULT TRUE,
                        version INTEGER NOT NULL DEFAULT 1,

    -- @Embedded Policy Fields
                        policy_token VARCHAR(255),
                        policy_type VARCHAR(100),
                        policy_start_date TIMESTAMP WITH TIME ZONE,
                        policy_end_date TIMESTAMP WITH TIME ZONE,

    -- @Embedded Claimant Fields
                        claimant_token VARCHAR(255),

    -- @Embedded Garage Fields
                        garage_id VARCHAR(255),

    -- @Embedded Incident Fields
                        incident_at TIMESTAMP WITH TIME ZONE,
                        reported_at TIMESTAMP WITH TIME ZONE,
                        permitted_location_precision VARCHAR(100),

    -- @Embedded Financial Fields
                        initial_estimate_amount BIGINT,
                        approved_estimate_amount BIGINT,
                        currency VARCHAR(10) DEFAULT 'USD',
                        deductible BIGINT,

    -- @Embedded Workflow Fields (Overridden names to prevent collisions)
                        status VARCHAR(50),
                        workflow_created_by VARCHAR(255), -- Maps to Java field 'ceratedBy'
                        workflow_created_at TIMESTAMP WITH TIME ZONE,
                        workflow_updated_at TIMESTAMP WITH TIME ZONE
);

-- Performance Optimization Indexes
CREATE INDEX idx_claims_organization_id ON claims(organization_id);
CREATE INDEX idx_claims_internal_id ON claims(internal_id);
CREATE INDEX idx_claims_status ON claims(status);
