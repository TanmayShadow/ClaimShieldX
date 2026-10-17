-- Backing store for the Idempotency-Key header (api-contract section 6). The response body is not stored:
-- a replay re-reads the resource named by resource_type and resource_id.
CREATE TABLE idempotency_keys (
    id                  UUID PRIMARY KEY,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    organization_id     UUID         NOT NULL,
    operation           VARCHAR(100) NOT NULL,
    idempotency_key     VARCHAR(128) NOT NULL,
    request_fingerprint VARCHAR(64)  NOT NULL,
    state               VARCHAR(20)  NOT NULL,
    response_status     INTEGER,
    resource_type       VARCHAR(40),
    resource_id         UUID,
    completed_at        TIMESTAMP WITH TIME ZONE,
    expires_at          TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT fk_idempotency_organization FOREIGN KEY (organization_id) REFERENCES organization (id),
    CONSTRAINT uq_idempotency_scope UNIQUE (organization_id, operation, idempotency_key),
    CONSTRAINT ck_idempotency_key_format CHECK (idempotency_key ~ '^[A-Za-z0-9._:-]{8,128}$'),
    CONSTRAINT ck_idempotency_fingerprint CHECK (request_fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_idempotency_state CHECK (state IN ('IN_PROGRESS', 'COMPLETED')),
    CONSTRAINT ck_idempotency_completed CHECK ((state = 'COMPLETED') = (completed_at IS NOT NULL AND response_status IS NOT NULL))
);

-- Expired keys are removed by a cleanup that scans on expiry time.
CREATE INDEX idx_idempotency_expires_at ON idempotency_keys (expires_at);
