-- Historical claims and garage history supplied by the insurer as CSV files, plus the record of each import and the
-- result of every row. Everything is owned by one organization and nothing here is ever shared across organizations.
--
-- Imported rows are immutable: a re-import of the same record is skipped, and a record that arrives again with different
-- content is rejected with a visible error. Nothing is overwritten, so a past analysis can always be explained later.

CREATE TABLE historical_imports (
    id                      UUID PRIMARY KEY,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    organization_id         UUID         NOT NULL,
    uploaded_by             UUID         NOT NULL,
    import_type             VARCHAR(30)  NOT NULL,
    template_version        VARCHAR(10)  NOT NULL,
    source                  VARCHAR(100),
    sha256                  VARCHAR(64)  NOT NULL,
    size_bytes              BIGINT       NOT NULL,
    status                  VARCHAR(30)  NOT NULL,
    failure_code            VARCHAR(40),
    -- A short, safe explanation (for example the names of missing template columns). Never contains text from the file.
    failure_detail          VARCHAR(500),
    total_rows              INTEGER      NOT NULL DEFAULT 0,
    accepted_rows           INTEGER      NOT NULL DEFAULT 0,
    rejected_rows           INTEGER      NOT NULL DEFAULT 0,
    skipped_duplicate_rows  INTEGER      NOT NULL DEFAULT 0,
    started_at              TIMESTAMP WITH TIME ZONE,
    finished_at             TIMESTAMP WITH TIME ZONE,

    CONSTRAINT fk_historical_imports_organization FOREIGN KEY (organization_id) REFERENCES organization (id),
    CONSTRAINT fk_historical_imports_uploaded_by FOREIGN KEY (uploaded_by) REFERENCES users (id),
    CONSTRAINT uq_historical_imports_id_organization UNIQUE (id, organization_id),
    CONSTRAINT ck_historical_imports_type CHECK (import_type IN ('HISTORICAL_CLAIMS', 'GARAGE_HISTORY')),
    CONSTRAINT ck_historical_imports_sha256 CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_historical_imports_size CHECK (size_bytes >= 0),
    CONSTRAINT ck_historical_imports_status CHECK (status IN ('RECEIVED', 'PROCESSING', 'COMPLETED', 'COMPLETED_WITH_ERRORS', 'FAILED')),
    -- Every row is accounted for exactly once, and a file-level failure stores no rows at all.
    CONSTRAINT ck_historical_imports_counts CHECK (
        total_rows >= 0 AND accepted_rows >= 0 AND rejected_rows >= 0 AND skipped_duplicate_rows >= 0 AND
        total_rows = accepted_rows + rejected_rows + skipped_duplicate_rows),
    CONSTRAINT ck_historical_imports_failure CHECK ((status = 'FAILED') = (failure_code IS NOT NULL)),
    CONSTRAINT ck_historical_imports_failed_empty CHECK (status <> 'FAILED' OR total_rows = 0),
    CONSTRAINT ck_historical_imports_finished CHECK ((status IN ('COMPLETED', 'COMPLETED_WITH_ERRORS', 'FAILED')) = (finished_at IS NOT NULL))
);

CREATE INDEX idx_historical_imports_org_created ON historical_imports (organization_id, created_at DESC);

CREATE TABLE historical_import_rows (
    id               UUID PRIMARY KEY,
    organization_id  UUID         NOT NULL,
    import_id        UUID         NOT NULL,
    row_number       INTEGER      NOT NULL,
    status           VARCHAR(30)  NOT NULL,
    source_record_id VARCHAR(100),
    errors           JSONB,

    CONSTRAINT fk_historical_import_rows_import FOREIGN KEY (import_id, organization_id) REFERENCES historical_imports (id, organization_id),
    CONSTRAINT uq_historical_import_rows_number UNIQUE (import_id, row_number),
    CONSTRAINT ck_historical_import_rows_number CHECK (row_number >= 1),
    CONSTRAINT ck_historical_import_rows_status CHECK (status IN ('ACCEPTED', 'REJECTED', 'SKIPPED_DUPLICATE')),
    -- A rejected row always says why; a row that was not rejected carries no errors.
    CONSTRAINT ck_historical_import_rows_errors CHECK (
        (status = 'REJECTED' AND errors IS NOT NULL AND jsonb_typeof(errors) = 'array' AND jsonb_array_length(errors) > 0) OR
        (status <> 'REJECTED' AND errors IS NULL))
);

CREATE INDEX idx_historical_import_rows_status ON historical_import_rows (import_id, status, row_number);

CREATE TABLE historical_claims (
    id                    UUID PRIMARY KEY,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    organization_id       UUID          NOT NULL,
    import_id             UUID          NOT NULL,
    row_number            INTEGER       NOT NULL,
    source_record_id      VARCHAR(100)  NOT NULL,
    external_claim_id     VARCHAR(100)  NOT NULL,
    claim_version         INTEGER       NOT NULL,
    vehicle_token         VARCHAR(255)  NOT NULL,
    policy_token          VARCHAR(255),
    claimant_token        VARCHAR(255),
    garage_id             VARCHAR(255),
    incident_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    incident_at_precision VARCHAR(10)   NOT NULL,
    reported_at           TIMESTAMP WITH TIME ZONE,
    claim_type            VARCHAR(30)   NOT NULL,
    claim_status          VARCHAR(40)   NOT NULL,
    outcome_status        VARCHAR(40),
    claimed_amount        NUMERIC(15, 2),
    approved_amount       NUMERIC(15, 2),
    currency              VARCHAR(3)    NOT NULL,
    repair_summary        VARCHAR(1000),
    damage_area_codes     JSONB         NOT NULL DEFAULT '[]'::jsonb,
    repair_completed_at   TIMESTAMP WITH TIME ZONE,
    content_hash          VARCHAR(64)   NOT NULL,

    CONSTRAINT fk_historical_claims_import FOREIGN KEY (import_id, organization_id) REFERENCES historical_imports (id, organization_id),
    CONSTRAINT uq_historical_claims_source UNIQUE (organization_id, source_record_id),
    CONSTRAINT uq_historical_claims_version UNIQUE (organization_id, external_claim_id, claim_version),
    CONSTRAINT ck_historical_claims_version CHECK (claim_version >= 1),
    CONSTRAINT ck_historical_claims_precision CHECK (incident_at_precision IN ('DATE', 'DATETIME')),
    CONSTRAINT ck_historical_claims_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_historical_claims_amounts CHECK (COALESCE(claimed_amount, 0) >= 0 AND COALESCE(approved_amount, 0) >= 0),
    CONSTRAINT ck_historical_claims_codes CHECK (jsonb_typeof(damage_area_codes) = 'array'),
    CONSTRAINT ck_historical_claims_hash CHECK (content_hash ~ '^[0-9a-f]{64}$')
);

-- Matching looks claims up by token, always within one organization.
CREATE INDEX idx_historical_claims_vehicle ON historical_claims (organization_id, vehicle_token);
CREATE INDEX idx_historical_claims_policy ON historical_claims (organization_id, policy_token) WHERE policy_token IS NOT NULL;
CREATE INDEX idx_historical_claims_claimant ON historical_claims (organization_id, claimant_token) WHERE claimant_token IS NOT NULL;
CREATE INDEX idx_historical_claims_garage ON historical_claims (organization_id, garage_id) WHERE garage_id IS NOT NULL;
CREATE INDEX idx_historical_claims_import ON historical_claims (import_id);

CREATE TABLE garage_history_records (
    id                              UUID PRIMARY KEY,
    created_at                      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    organization_id                 UUID          NOT NULL,
    import_id                       UUID          NOT NULL,
    row_number                      INTEGER       NOT NULL,
    source_record_id                VARCHAR(100)  NOT NULL,
    garage_id                       VARCHAR(255)  NOT NULL,
    reporting_period_start          DATE          NOT NULL,
    reporting_period_end            DATE          NOT NULL,
    claim_count                     INTEGER       NOT NULL,
    claimed_total                   NUMERIC(15, 2),
    approved_total                  NUMERIC(15, 2),
    currency                        VARCHAR(3)    NOT NULL,
    estimate_invoice_variance_count INTEGER,
    disputed_or_reopened_count      INTEGER,
    confirmed_outcome_count         INTEGER,
    content_hash                    VARCHAR(64)   NOT NULL,

    CONSTRAINT fk_garage_history_import FOREIGN KEY (import_id, organization_id) REFERENCES historical_imports (id, organization_id),
    CONSTRAINT uq_garage_history_source UNIQUE (organization_id, source_record_id),
    CONSTRAINT ck_garage_history_period CHECK (reporting_period_end >= reporting_period_start),
    CONSTRAINT ck_garage_history_counts CHECK (
        claim_count >= 0 AND
        COALESCE(estimate_invoice_variance_count, 0) BETWEEN 0 AND claim_count AND
        COALESCE(disputed_or_reopened_count, 0) BETWEEN 0 AND claim_count AND
        COALESCE(confirmed_outcome_count, 0) BETWEEN 0 AND claim_count),
    CONSTRAINT ck_garage_history_amounts CHECK (COALESCE(claimed_total, 0) >= 0 AND COALESCE(approved_total, 0) >= 0),
    CONSTRAINT ck_garage_history_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_garage_history_hash CHECK (content_hash ~ '^[0-9a-f]{64}$')
);

CREATE INDEX idx_garage_history_garage ON garage_history_records (organization_id, garage_id, reporting_period_start);
CREATE INDEX idx_garage_history_import ON garage_history_records (import_id);
