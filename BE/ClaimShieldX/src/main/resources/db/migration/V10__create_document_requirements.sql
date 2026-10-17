-- Which document is required, optional or not applicable at each claim stage. These are configurable product
-- defaults, not legal requirements: the insurer must confirm them (data-contract section 6). They live in a table
-- so a different insurer can be served by changing rows, not code.
--
-- incident_type is 'ANY' (applies to every claim) or a specific incident type. A specific row replaces the 'ANY'
-- row for the same stage and document type. incident_type has no CHECK on purpose: like claims.incident_type, it
-- is a taxonomy expected to grow, and the Java enum validates it.
--
-- gating marks the one document that defines a stage (the claim form at FNOL, the garage estimate when an estimate
-- has been received, the final invoice at the final stage). Without it the claim is NOT_READY. Every other missing
-- document only lowers the readiness to READY_WITH_WARNINGS and names the checks that cannot run, so preliminary
-- checks may proceed with incomplete evidence.
CREATE TABLE document_requirements (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    stage         VARCHAR(30) NOT NULL,
    incident_type VARCHAR(30) NOT NULL DEFAULT 'ANY',
    document_type VARCHAR(40) NOT NULL,
    requirement   VARCHAR(20) NOT NULL,
    gating        BOOLEAN     NOT NULL DEFAULT FALSE,

    CONSTRAINT uq_document_requirements_key UNIQUE (stage, incident_type, document_type),
    CONSTRAINT ck_document_requirements_stage CHECK (stage IN
        ('FNOL', 'ESTIMATE_RECEIVED', 'FINAL_INVOICE_RECEIVED', 'POST_SETTLEMENT')),
    CONSTRAINT ck_document_requirements_requirement CHECK (requirement IN ('REQUIRED', 'OPTIONAL', 'NOT_APPLICABLE')),
    CONSTRAINT ck_document_requirements_gating CHECK (NOT gating OR requirement = 'REQUIRED')
);

-- POST_SETTLEMENT has no checklist in the prototype, so it has no rows.
INSERT INTO document_requirements (stage, incident_type, document_type, requirement, gating) VALUES
    ('FNOL',                   'ANY', 'CLAIM_FORM',             'REQUIRED',       TRUE),
    ('ESTIMATE_RECEIVED',      'ANY', 'CLAIM_FORM',             'REQUIRED',       FALSE),
    ('FINAL_INVOICE_RECEIVED', 'ANY', 'CLAIM_FORM',             'REQUIRED',       FALSE),

    ('FNOL',                   'ANY', 'POLICY_RECORD',          'REQUIRED',       FALSE),
    ('ESTIMATE_RECEIVED',      'ANY', 'POLICY_RECORD',          'REQUIRED',       FALSE),
    ('FINAL_INVOICE_RECEIVED', 'ANY', 'POLICY_RECORD',          'REQUIRED',       FALSE),

    ('FNOL',                   'ANY', 'VEHICLE_REGISTRATION',   'OPTIONAL',       FALSE),
    ('ESTIMATE_RECEIVED',      'ANY', 'VEHICLE_REGISTRATION',   'OPTIONAL',       FALSE),
    ('FINAL_INVOICE_RECEIVED', 'ANY', 'VEHICLE_REGISTRATION',   'OPTIONAL',       FALSE),

    ('FNOL',                   'ANY', 'DRIVING_LICENCE',        'OPTIONAL',       FALSE),
    ('ESTIMATE_RECEIVED',      'ANY', 'DRIVING_LICENCE',        'OPTIONAL',       FALSE),
    ('FINAL_INVOICE_RECEIVED', 'ANY', 'DRIVING_LICENCE',        'OPTIONAL',       FALSE),

    ('FNOL',                   'ANY', 'DAMAGE_PHOTOGRAPH',      'OPTIONAL',       FALSE),
    ('ESTIMATE_RECEIVED',      'ANY', 'DAMAGE_PHOTOGRAPH',      'OPTIONAL',       FALSE),
    ('FINAL_INVOICE_RECEIVED', 'ANY', 'DAMAGE_PHOTOGRAPH',      'OPTIONAL',       FALSE),

    ('FNOL',                   'ANY', 'GARAGE_ESTIMATE',        'NOT_APPLICABLE', FALSE),
    ('ESTIMATE_RECEIVED',      'ANY', 'GARAGE_ESTIMATE',        'REQUIRED',       TRUE),
    ('FINAL_INVOICE_RECEIVED', 'ANY', 'GARAGE_ESTIMATE',        'REQUIRED',       FALSE),

    ('FNOL',                   'ANY', 'JOB_CARD',               'NOT_APPLICABLE', FALSE),
    ('ESTIMATE_RECEIVED',      'ANY', 'JOB_CARD',               'OPTIONAL',       FALSE),
    ('FINAL_INVOICE_RECEIVED', 'ANY', 'JOB_CARD',               'OPTIONAL',       FALSE),

    ('FNOL',                   'ANY', 'SURVEYOR_REPORT',        'OPTIONAL',       FALSE),
    ('ESTIMATE_RECEIVED',      'ANY', 'SURVEYOR_REPORT',        'OPTIONAL',       FALSE),
    ('FINAL_INVOICE_RECEIVED', 'ANY', 'SURVEYOR_REPORT',        'OPTIONAL',       FALSE),

    ('FNOL',                   'ANY', 'FINAL_INVOICE',          'NOT_APPLICABLE', FALSE),
    ('ESTIMATE_RECEIVED',      'ANY', 'FINAL_INVOICE',          'NOT_APPLICABLE', FALSE),
    ('FINAL_INVOICE_RECEIVED', 'ANY', 'FINAL_INVOICE',          'REQUIRED',       TRUE),

    ('FNOL',                   'ANY', 'SUPPLEMENTARY_ESTIMATE', 'NOT_APPLICABLE', FALSE),
    ('ESTIMATE_RECEIVED',      'ANY', 'SUPPLEMENTARY_ESTIMATE', 'OPTIONAL',       FALSE),
    ('FINAL_INVOICE_RECEIVED', 'ANY', 'SUPPLEMENTARY_ESTIMATE', 'OPTIONAL',       FALSE),

    ('FNOL',                   'ANY', 'PAYMENT_PROOF',          'NOT_APPLICABLE', FALSE),
    ('ESTIMATE_RECEIVED',      'ANY', 'PAYMENT_PROOF',          'NOT_APPLICABLE', FALSE),
    ('FINAL_INVOICE_RECEIVED', 'ANY', 'PAYMENT_PROOF',          'OPTIONAL',       FALSE),

    ('FNOL',                   'ANY', 'FIR_POLICE_REPORT',      'OPTIONAL',       FALSE),
    ('ESTIMATE_RECEIVED',      'ANY', 'FIR_POLICE_REPORT',      'OPTIONAL',       FALSE),
    ('FINAL_INVOICE_RECEIVED', 'ANY', 'FIR_POLICE_REPORT',      'OPTIONAL',       FALSE),

    ('FNOL',                   'ANY', 'THEFT_EVIDENCE',         'NOT_APPLICABLE', FALSE),
    ('ESTIMATE_RECEIVED',      'ANY', 'THEFT_EVIDENCE',         'NOT_APPLICABLE', FALSE),
    ('FINAL_INVOICE_RECEIVED', 'ANY', 'THEFT_EVIDENCE',         'NOT_APPLICABLE', FALSE);

-- Theft claims: an FIR and theft-specific evidence are required at every stage.
INSERT INTO document_requirements (stage, incident_type, document_type, requirement, gating) VALUES
    ('FNOL',                   'THEFT', 'FIR_POLICE_REPORT', 'REQUIRED', FALSE),
    ('ESTIMATE_RECEIVED',      'THEFT', 'FIR_POLICE_REPORT', 'REQUIRED', FALSE),
    ('FINAL_INVOICE_RECEIVED', 'THEFT', 'FIR_POLICE_REPORT', 'REQUIRED', FALSE),
    ('FNOL',                   'THEFT', 'THEFT_EVIDENCE',    'REQUIRED', FALSE),
    ('ESTIMATE_RECEIVED',      'THEFT', 'THEFT_EVIDENCE',    'REQUIRED', FALSE),
    ('FINAL_INVOICE_RECEIVED', 'THEFT', 'THEFT_EVIDENCE',    'REQUIRED', FALSE);
