-- A document's organization must be its claim's organization. This composite key lets the database
-- guarantee that, instead of relying only on service code.
ALTER TABLE claims ADD CONSTRAINT uq_claims_id_organization UNIQUE (id, organization_id);

CREATE TABLE documents (
    id                     UUID PRIMARY KEY,
    created_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version                BIGINT       NOT NULL DEFAULT 0,
    organization_id        UUID         NOT NULL,
    claim_id               UUID         NOT NULL,
    document_type          VARCHAR(40)  NOT NULL,
    file_name              VARCHAR(255) NOT NULL,
    content_type           VARCHAR(100) NOT NULL,
    size_bytes             BIGINT       NOT NULL,
    sha256                 VARCHAR(64)  NOT NULL,
    object_key             VARCHAR(255) NOT NULL,
    status                 VARCHAR(30)  NOT NULL DEFAULT 'RECEIVED',
    external_document_id   VARCHAR(255),
    supersedes_document_id UUID,
    superseded             BOOLEAN      NOT NULL DEFAULT FALSE,
    uploaded_by            UUID         NOT NULL,
    received_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_documents_organization FOREIGN KEY (organization_id) REFERENCES organization (id),
    CONSTRAINT fk_documents_claim FOREIGN KEY (claim_id, organization_id) REFERENCES claims (id, organization_id),
    CONSTRAINT fk_documents_uploaded_by FOREIGN KEY (uploaded_by) REFERENCES users (id),

    -- A revision must belong to the same claim and have the same type as the file it replaces.
    CONSTRAINT uq_documents_id_claim_type UNIQUE (id, claim_id, document_type),
    CONSTRAINT fk_documents_supersedes FOREIGN KEY (supersedes_document_id, claim_id, document_type)
        REFERENCES documents (id, claim_id, document_type),

    CONSTRAINT uq_documents_object_key UNIQUE (object_key),
    CONSTRAINT ck_documents_size_positive CHECK (size_bytes > 0),
    CONSTRAINT ck_documents_sha256 CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_documents_status CHECK (status IN ('RECEIVED', 'PROCESSING', 'VERIFIED', 'NEEDS_REVIEW', 'FAILED')),
    CONSTRAINT ck_documents_not_self_superseding CHECK (supersedes_document_id IS NULL OR supersedes_document_id <> id)
);

-- The same file cannot be stored twice for the same claim and type, unless the earlier copy was
-- superseded. The same file on a different claim is allowed on purpose (cross-claim repeats are a rule signal).
CREATE UNIQUE INDEX uq_documents_active_checksum ON documents (claim_id, document_type, sha256) WHERE NOT superseded;

-- A document can be replaced at most once.
CREATE UNIQUE INDEX uq_documents_superseded_once ON documents (supersedes_document_id) WHERE supersedes_document_id IS NOT NULL;

-- The checklist asks for the latest document of each type per claim.
CREATE INDEX idx_documents_claim_type_received ON documents (claim_id, document_type, received_at DESC);
