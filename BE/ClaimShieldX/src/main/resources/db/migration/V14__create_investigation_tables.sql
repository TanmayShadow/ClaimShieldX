-- An investigation is the durable job: the row carries its own queue state (lease, attempts, next attempt time), so there is one
-- source of truth and nothing to keep in step. A worker claims a row with FOR UPDATE SKIP LOCKED in one statement, renews the
-- lease while it works, and a row whose lease has expired (a crashed worker) can be claimed again.
--
-- The work is split into stages. investigation_stages holds each stage's result, written before the next stage starts, so a retry
-- or a crash resumes at the first unfinished stage and never repeats an expensive, finished one.
CREATE TABLE investigations (
    id                UUID PRIMARY KEY,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    organization_id   UUID         NOT NULL,
    claim_id          UUID         NOT NULL,
    claim_version     BIGINT       NOT NULL,
    requested_by      UUID         NOT NULL,
    note              VARCHAR(500),
    status            VARCHAR(30)  NOT NULL,
    -- The attempt in progress (or next to start). It grows on a transient failure and when a crashed worker's job is claimed again.
    attempt           INTEGER      NOT NULL DEFAULT 1,
    max_attempts      INTEGER      NOT NULL,
    next_attempt_at   TIMESTAMP WITH TIME ZONE,
    lease_owner       VARCHAR(100),
    lease_expires_at  TIMESTAMP WITH TIME ZONE,
    cancel_requested  BOOLEAN      NOT NULL DEFAULT FALSE,
    started_at        TIMESTAMP WITH TIME ZONE,
    finished_at       TIMESTAMP WITH TIME ZONE,
    -- Safe values only: a code from a fixed list and a fixed message. Never an exception message or anything from a document.
    error_code        VARCHAR(40),
    error_message     VARCHAR(300),
    rule_run_id       UUID,

    CONSTRAINT fk_investigations_claim FOREIGN KEY (claim_id, organization_id) REFERENCES claims (id, organization_id),
    CONSTRAINT fk_investigations_requested_by FOREIGN KEY (requested_by) REFERENCES users (id),
    CONSTRAINT fk_investigations_rule_run FOREIGN KEY (rule_run_id, claim_id, organization_id) REFERENCES rule_runs (id, claim_id, organization_id),
    CONSTRAINT uq_investigations_id_claim_organization UNIQUE (id, claim_id, organization_id),
    CONSTRAINT ck_investigations_status CHECK (status IN ('QUEUED', 'PREPARING_EVIDENCE', 'EXTRACTING', 'RUNNING_RULES', 'BUILDING_HYPOTHESES',
        'VALIDATING_FINDINGS', 'GENERATING_REPORT', 'WAITING_FOR_EVIDENCE', 'RETRY_WAIT', 'COMPLETED', 'FAILED', 'CANCELLED')),
    CONSTRAINT ck_investigations_attempts CHECK (attempt >= 1 AND max_attempts >= 1),
    CONSTRAINT ck_investigations_lease CHECK ((lease_owner IS NULL) = (lease_expires_at IS NULL)),
    -- A finished investigation holds no lease, and a failed one says why.
    CONSTRAINT ck_investigations_terminal_lease CHECK (status NOT IN ('COMPLETED', 'FAILED', 'CANCELLED') OR lease_owner IS NULL),
    CONSTRAINT ck_investigations_finished CHECK ((status IN ('COMPLETED', 'FAILED', 'CANCELLED')) = (finished_at IS NOT NULL)),
    CONSTRAINT ck_investigations_failed_reason CHECK (status <> 'FAILED' OR error_code IS NOT NULL)
);

-- One investigation at a time per claim. FAILED is not "active": the claim may be investigated again, and retrying the failed one
-- while another is active is refused by this same index.
CREATE UNIQUE INDEX uq_investigations_one_active_per_claim ON investigations (claim_id)
    WHERE status NOT IN ('COMPLETED', 'FAILED', 'CANCELLED');

-- The worker's question: which unfinished investigation is due, oldest first.
CREATE INDEX idx_investigations_claimable ON investigations (next_attempt_at, created_at)
    WHERE status NOT IN ('COMPLETED', 'FAILED', 'CANCELLED');
CREATE INDEX idx_investigations_claim_created ON investigations (claim_id, created_at DESC, id);
-- The daily limit counts an organization's recent investigations.
CREATE INDEX idx_investigations_organization_created ON investigations (organization_id, created_at DESC);

CREATE TABLE investigation_stages (
    id                UUID PRIMARY KEY,
    investigation_id  UUID         NOT NULL,
    organization_id   UUID         NOT NULL,
    claim_id          UUID         NOT NULL,
    stage             VARCHAR(30)  NOT NULL,
    status            VARCHAR(12)  NOT NULL,
    attempts          INTEGER      NOT NULL DEFAULT 0,
    started_at        TIMESTAMP WITH TIME ZONE,
    finished_at       TIMESTAMP WITH TIME ZONE,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    error_code        VARCHAR(40),
    -- Counts and ids only, never document content.
    summary           JSONB        NOT NULL DEFAULT '{}'::jsonb,

    CONSTRAINT fk_investigation_stages_investigation FOREIGN KEY (investigation_id, claim_id, organization_id)
        REFERENCES investigations (id, claim_id, organization_id),
    CONSTRAINT uq_investigation_stages_stage UNIQUE (investigation_id, stage),
    CONSTRAINT ck_investigation_stages_stage CHECK (stage IN ('PREPARING_EVIDENCE', 'EXTRACTING', 'RUNNING_RULES', 'BUILDING_HYPOTHESES',
        'VALIDATING_FINDINGS', 'GENERATING_REPORT')),
    CONSTRAINT ck_investigation_stages_status CHECK (status IN ('PENDING', 'RUNNING', 'OK', 'DEGRADED', 'FAILED', 'SKIPPED')),
    CONSTRAINT ck_investigation_stages_attempts CHECK (attempts >= 0)
);
