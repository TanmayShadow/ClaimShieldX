-- B9 to B12: what an investigation produces. Everything here is written once per investigation and never edited, so a report can
-- always be traced to the exact evidence it was built from.

-- B9: the frozen evidence package of one investigation. Every evidence item has a stable reference (for example RULE.INVOICE_ARITHMETIC)
-- that is unique inside the snapshot; findings and model answers may only cite references that exist in their own snapshot.
CREATE TABLE evidence_snapshots (
    id                UUID PRIMARY KEY,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    organization_id   UUID        NOT NULL,
    claim_id          UUID        NOT NULL,
    investigation_id  UUID        NOT NULL,
    claim_version     BIGINT      NOT NULL,
    rule_run_id       UUID        NOT NULL,
    rule_set_version  VARCHAR(20) NOT NULL,
    input_fingerprint VARCHAR(64) NOT NULL,
    history_import_id UUID,
    item_count        INTEGER     NOT NULL,
    CONSTRAINT fk_evidence_snapshots_claim FOREIGN KEY (claim_id, organization_id) REFERENCES claims (id, organization_id),
    CONSTRAINT fk_evidence_snapshots_investigation FOREIGN KEY (investigation_id, claim_id, organization_id) REFERENCES investigations (id, claim_id, organization_id),
    CONSTRAINT fk_evidence_snapshots_rule_run FOREIGN KEY (rule_run_id, claim_id, organization_id) REFERENCES rule_runs (id, claim_id, organization_id),
    CONSTRAINT uq_evidence_snapshots_investigation UNIQUE (investigation_id),
    CONSTRAINT uq_evidence_snapshots_id_claim_organization UNIQUE (id, claim_id, organization_id),
    CONSTRAINT ck_evidence_snapshots_count CHECK (item_count >= 0)
);

CREATE TABLE evidence_items (
    id              UUID PRIMARY KEY,
    snapshot_id     UUID         NOT NULL,
    organization_id UUID         NOT NULL,
    claim_id        UUID         NOT NULL,
    ref             VARCHAR(160) NOT NULL,
    item_type       VARCHAR(30)  NOT NULL,
    -- VERIFIED_FACT: provided by the insurer or computed by the system. EXTRACTED: read from a document by the model (never "true" on its
    -- own). DETERMINISTIC_RESULT: a rule's outcome. CONTEXT: history, a relationship. UNKNOWN: evidence that is missing.
    state           VARCHAR(30)  NOT NULL,
    label           VARCHAR(300) NOT NULL,
    data            JSONB        NOT NULL,
    CONSTRAINT fk_evidence_items_snapshot FOREIGN KEY (snapshot_id, claim_id, organization_id) REFERENCES evidence_snapshots (id, claim_id, organization_id),
    CONSTRAINT uq_evidence_items_ref UNIQUE (snapshot_id, ref),
    CONSTRAINT ck_evidence_items_type CHECK (item_type IN ('CLAIM_FIELD', 'DOCUMENT', 'EXTRACTED_FIELD', 'RULE_RESULT', 'HISTORICAL_RECORD', 'CHECKLIST_ITEM', 'RELATIONSHIP', 'UNKNOWN')),
    CONSTRAINT ck_evidence_items_state CHECK (state IN ('VERIFIED_FACT', 'EXTRACTED', 'EXTRACTED_NEEDS_REVIEW', 'DETERMINISTIC_RESULT', 'CONTEXT', 'UNKNOWN'))
);
CREATE INDEX idx_evidence_items_snapshot ON evidence_items (snapshot_id);

-- B10: the answer of each model role (H1 suspicion analyst, H2 counter-evidence analyst, H3 evidence reviewer), after Java validated it.
-- A role that could not answer is stored as UNAVAILABLE with a code, so "no answer" is never mistaken for "found nothing".
CREATE TABLE investigation_analyses (
    id               UUID PRIMARY KEY,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    organization_id  UUID         NOT NULL,
    claim_id         UUID         NOT NULL,
    investigation_id UUID         NOT NULL,
    role             VARCHAR(4)   NOT NULL,
    status           VARCHAR(12)  NOT NULL,
    model_id         VARCHAR(100),
    prompt_version   VARCHAR(40)  NOT NULL,
    model_calls      INTEGER      NOT NULL DEFAULT 0,
    input_tokens     INTEGER,
    output_tokens    INTEGER,
    error_code       VARCHAR(40),
    result           JSONB        NOT NULL,
    CONSTRAINT fk_investigation_analyses_investigation FOREIGN KEY (investigation_id, claim_id, organization_id) REFERENCES investigations (id, claim_id, organization_id),
    CONSTRAINT uq_investigation_analyses_role UNIQUE (investigation_id, role),
    CONSTRAINT ck_investigation_analyses_role CHECK (role IN ('H1', 'H2', 'H3')),
    CONSTRAINT ck_investigation_analyses_status CHECK (status IN ('OK', 'UNAVAILABLE')),
    CONSTRAINT ck_investigation_analyses_error CHECK ((status = 'UNAVAILABLE') = (error_code IS NOT NULL))
);

-- B11: findings. Only VALIDATED ones are published. A hypothesis the reviewer could not support is kept as REJECTED with the reason.
CREATE TABLE findings (
    id               UUID PRIMARY KEY,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    organization_id  UUID         NOT NULL,
    claim_id         UUID         NOT NULL,
    investigation_id UUID         NOT NULL,
    snapshot_id      UUID         NOT NULL,
    ordinal          INTEGER      NOT NULL,
    source           VARCHAR(60)  NOT NULL,
    category         VARCHAR(40)  NOT NULL,
    title            VARCHAR(200) NOT NULL,
    summary          TEXT         NOT NULL,
    finding_type     VARCHAR(12)  NOT NULL,
    status           VARCHAR(30)  NOT NULL,
    validation_state VARCHAR(10)  NOT NULL,
    rejection_reason VARCHAR(200),
    supporting_refs  JSONB        NOT NULL,
    counter_refs     JSONB        NOT NULL,
    counter_evidence JSONB        NOT NULL,
    missing_checks   JSONB        NOT NULL,
    recommendation   TEXT         NOT NULL,
    CONSTRAINT fk_findings_investigation FOREIGN KEY (investigation_id, claim_id, organization_id) REFERENCES investigations (id, claim_id, organization_id),
    CONSTRAINT fk_findings_snapshot FOREIGN KEY (snapshot_id, claim_id, organization_id) REFERENCES evidence_snapshots (id, claim_id, organization_id),
    CONSTRAINT uq_findings_ordinal UNIQUE (investigation_id, ordinal),
    CONSTRAINT ck_findings_type CHECK (finding_type IN ('FACT', 'INFERENCE', 'HYPOTHESIS', 'UNKNOWN')),
    CONSTRAINT ck_findings_validation CHECK (validation_state IN ('VALIDATED', 'REJECTED')),
    CONSTRAINT ck_findings_rejection CHECK ((validation_state = 'REJECTED') = (rejection_reason IS NOT NULL))
);

-- B11/B12: one report per investigation. report_version counts the claim's reports, so a newer investigation is a newer version.
CREATE TABLE reports (
    id                  UUID PRIMARY KEY,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    organization_id     UUID         NOT NULL,
    claim_id            UUID         NOT NULL,
    investigation_id    UUID         NOT NULL,
    snapshot_id         UUID         NOT NULL,
    report_version      INTEGER      NOT NULL,
    completeness        VARCHAR(10)  NOT NULL,
    unavailable_sections JSONB       NOT NULL,
    summary             TEXT         NOT NULL,
    score               INTEGER      NOT NULL,
    score_band          VARCHAR(10)  NOT NULL,
    recommended_action  VARCHAR(40)  NOT NULL,
    components          JSONB        NOT NULL,
    score_version       VARCHAR(20)  NOT NULL,
    rule_set_version    VARCHAR(20)  NOT NULL,
    CONSTRAINT fk_reports_investigation FOREIGN KEY (investigation_id, claim_id, organization_id) REFERENCES investigations (id, claim_id, organization_id),
    CONSTRAINT fk_reports_snapshot FOREIGN KEY (snapshot_id, claim_id, organization_id) REFERENCES evidence_snapshots (id, claim_id, organization_id),
    CONSTRAINT uq_reports_investigation UNIQUE (investigation_id),
    CONSTRAINT uq_reports_claim_version UNIQUE (claim_id, report_version),
    CONSTRAINT uq_reports_id_claim_organization UNIQUE (id, claim_id, organization_id),
    CONSTRAINT ck_reports_completeness CHECK (completeness IN ('COMPLETE', 'PARTIAL')),
    CONSTRAINT ck_reports_score CHECK (score BETWEEN 0 AND 100),
    CONSTRAINT ck_reports_band CHECK (score_band IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT ck_reports_action CHECK (recommended_action IN ('REQUEST_MORE_EVIDENCE', 'MANUAL_REVIEW', 'NO_ADDITIONAL_CHECK_INDICATED'))
);

-- B12: what a person decided after reading a report. Append-only: nothing in the application updates or deletes a row.
CREATE TABLE review_actions (
    id                 UUID PRIMARY KEY,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    organization_id    UUID         NOT NULL,
    claim_id           UUID         NOT NULL,
    investigation_id   UUID         NOT NULL,
    report_id          UUID         NOT NULL,
    snapshot_id        UUID         NOT NULL,
    created_by         UUID         NOT NULL,
    action             VARCHAR(40)  NOT NULL,
    rationale          TEXT         NOT NULL,
    report_version     INTEGER      NOT NULL,
    -- What the tool had said at that moment, so the decision can be understood later even if newer reports exist.
    report_was_stale   BOOLEAN      NOT NULL,
    recommended_action VARCHAR(40)  NOT NULL,
    score              INTEGER      NOT NULL,
    CONSTRAINT fk_review_actions_report FOREIGN KEY (report_id, claim_id, organization_id) REFERENCES reports (id, claim_id, organization_id),
    CONSTRAINT fk_review_actions_user FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT ck_review_actions_action CHECK (action IN ('REQUEST_MORE_EVIDENCE', 'ESCALATE_FOR_FURTHER_INVESTIGATION', 'NO_FURTHER_CHECKS_NEEDED', 'DEFER_REVIEW')),
    CONSTRAINT ck_review_actions_rationale CHECK (char_length(rationale) BETWEEN 10 AND 2000)
);
CREATE INDEX idx_review_actions_investigation ON review_actions (investigation_id, created_at);

CREATE TABLE audit_events (
    id               UUID PRIMARY KEY,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    organization_id  UUID        NOT NULL,
    claim_id         UUID        NOT NULL,
    investigation_id UUID,
    actor_user_id    UUID,
    event_type       VARCHAR(40) NOT NULL,
    detail           JSONB       NOT NULL,
    CONSTRAINT fk_audit_events_claim FOREIGN KEY (claim_id, organization_id) REFERENCES claims (id, organization_id),
    CONSTRAINT fk_audit_events_user FOREIGN KEY (actor_user_id) REFERENCES users (id)
);
CREATE INDEX idx_audit_events_investigation ON audit_events (investigation_id, created_at);
CREATE INDEX idx_audit_events_claim ON audit_events (claim_id, created_at);
