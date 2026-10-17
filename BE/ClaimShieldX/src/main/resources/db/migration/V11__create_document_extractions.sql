-- One row per extraction attempt on a document. Rows are never updated: a retry adds a new row, so the history
-- of what the model produced (and why an attempt failed) is kept. The newest row is the current extraction.
--
-- result holds the validated, typed extraction as JSON: every field with its original text, its normalized value,
-- its source (page and quote), the totals recalculated by Java, and the review issues found. The raw model output
-- and the prompt are deliberately NOT stored (they are not needed after validation and may hold sensitive text).
CREATE TABLE document_extractions (
    id              UUID PRIMARY KEY,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    organization_id UUID         NOT NULL,
    claim_id        UUID         NOT NULL,
    document_id     UUID         NOT NULL,
    document_type   VARCHAR(40)  NOT NULL,
    attempt         INTEGER      NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    schema_version  VARCHAR(40)  NOT NULL,
    prompt_version  VARCHAR(40)  NOT NULL,
    model_id        VARCHAR(100) NOT NULL,
    failure_code    VARCHAR(60),
    page_count      INTEGER,
    result          JSONB,
    model_calls     INTEGER      NOT NULL DEFAULT 0,
    input_tokens    INTEGER,
    output_tokens   INTEGER,
    duration_ms     BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT fk_document_extractions_claim FOREIGN KEY (claim_id, organization_id) REFERENCES claims (id, organization_id),
    -- The extraction's claim and type must be the document's own (see uq_documents_id_claim_type in V8).
    CONSTRAINT fk_document_extractions_document FOREIGN KEY (document_id, claim_id, document_type)
        REFERENCES documents (id, claim_id, document_type),
    CONSTRAINT uq_document_extractions_attempt UNIQUE (document_id, attempt),
    CONSTRAINT ck_document_extractions_attempt CHECK (attempt >= 1),
    CONSTRAINT ck_document_extractions_status CHECK (status IN ('SUCCEEDED', 'FAILED')),
    -- A success carries its result; a failure carries a code and never a half-built result.
    CONSTRAINT ck_document_extractions_outcome CHECK (
        (status = 'SUCCEEDED' AND result IS NOT NULL AND failure_code IS NULL) OR
        (status = 'FAILED' AND result IS NULL AND failure_code IS NOT NULL)),
    CONSTRAINT ck_document_extractions_counts CHECK (
        model_calls >= 0 AND duration_ms >= 0 AND COALESCE(page_count, 1) >= 1 AND
        COALESCE(input_tokens, 0) >= 0 AND COALESCE(output_tokens, 0) >= 0)
);

-- "The latest extraction of this document" is the common question.
CREATE INDEX idx_document_extractions_document_attempt ON document_extractions (document_id, attempt DESC);
