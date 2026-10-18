-- A rule run is one evaluation of every deterministic rule for one claim, and rule_results holds one row per rule of that run.
-- Both are written once and never updated: a later run is a new row, so a report can always point at exactly the results it used.
--
-- input_fingerprint is a hash of everything the rules were allowed to look at (the claim, the checklist, the documents with their
-- extraction ids, identical files and same-number invoices elsewhere in the organization, and the history lookup). The same
-- fingerprint with the same rule-set version and configuration always gives the same results, so the database refuses a second
-- run with identical inputs and the service returns the first one instead.
CREATE TABLE rule_runs (
    id                  UUID PRIMARY KEY,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    organization_id     UUID        NOT NULL,
    claim_id            UUID        NOT NULL,
    requested_by        UUID,
    rule_set_version    VARCHAR(20) NOT NULL,
    -- The thresholds and tolerances in force, so a run can be explained after the configuration changes.
    config              JSONB       NOT NULL,
    config_sha256       VARCHAR(64) NOT NULL,
    input_fingerprint   VARCHAR(64) NOT NULL,
    -- A compact description of what was used (ids and hashes, not content); the content itself lives in the rows it points to.
    inputs              JSONB       NOT NULL,
    pass_count          INTEGER     NOT NULL,
    flag_count          INTEGER     NOT NULL,
    incomplete_count    INTEGER     NOT NULL,
    not_applicable_count INTEGER    NOT NULL,
    error_count         INTEGER     NOT NULL,

    CONSTRAINT fk_rule_runs_claim FOREIGN KEY (claim_id, organization_id) REFERENCES claims (id, organization_id),
    CONSTRAINT fk_rule_runs_requested_by FOREIGN KEY (requested_by) REFERENCES users (id),
    CONSTRAINT uq_rule_runs_id_claim_organization UNIQUE (id, claim_id, organization_id),
    CONSTRAINT uq_rule_runs_inputs UNIQUE (claim_id, rule_set_version, config_sha256, input_fingerprint),
    CONSTRAINT ck_rule_runs_hashes CHECK (config_sha256 ~ '^[0-9a-f]{64}$' AND input_fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_rule_runs_counts CHECK (pass_count >= 0 AND flag_count >= 0 AND incomplete_count >= 0 AND not_applicable_count >= 0 AND error_count >= 0)
);

-- "The newest run of this claim" is the common question.
CREATE INDEX idx_rule_runs_claim_created ON rule_runs (claim_id, created_at DESC, id);

CREATE TABLE rule_results (
    id                  UUID PRIMARY KEY,
    organization_id     UUID        NOT NULL,
    claim_id            UUID        NOT NULL,
    run_id              UUID        NOT NULL,
    rule_id             VARCHAR(60) NOT NULL,
    rule_version        VARCHAR(20) NOT NULL,
    outcome             VARCHAR(20) NOT NULL,
    severity            VARCHAR(10),
    reliability         VARCHAR(10) NOT NULL,
    reliability_reasons JSONB       NOT NULL,
    explanation         TEXT        NOT NULL,
    result_values       JSONB       NOT NULL,
    evidence            JSONB       NOT NULL,
    considerations      JSONB       NOT NULL,
    executed_at         TIMESTAMP WITH TIME ZONE NOT NULL,

    -- The result's claim and organization must be its run's own.
    CONSTRAINT fk_rule_results_run FOREIGN KEY (run_id, claim_id, organization_id) REFERENCES rule_runs (id, claim_id, organization_id),
    CONSTRAINT uq_rule_results_run_rule UNIQUE (run_id, rule_id),
    CONSTRAINT ck_rule_results_outcome CHECK (outcome IN ('PASS', 'FLAG', 'INCOMPLETE', 'NOT_APPLICABLE', 'ERROR')),
    CONSTRAINT ck_rule_results_severity CHECK (severity IS NULL OR severity IN ('INFO', 'REVIEW')),
    -- A severity exists exactly when something was flagged.
    CONSTRAINT ck_rule_results_severity_with_flag CHECK ((outcome = 'FLAG') = (severity IS NOT NULL)),
    CONSTRAINT ck_rule_results_reliability CHECK (reliability IN ('RELIABLE', 'DOUBTFUL'))
);

CREATE INDEX idx_rule_results_run ON rule_results (run_id);

-- Finding the same file elsewhere in the organization (exact file duplicate rule).
CREATE INDEX idx_documents_organization_sha256 ON documents (organization_id, sha256);

-- Finding final invoices that share an invoice number (invoice identity rule) only ever looks at successful invoice extractions.
CREATE INDEX idx_document_extractions_invoices ON document_extractions (organization_id, created_at DESC)
    WHERE document_type = 'FINAL_INVOICE' AND status = 'SUCCEEDED';
