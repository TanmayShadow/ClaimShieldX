-- Money: whole-number BIGINT cannot hold paise, which invoices carry (decision D1).
ALTER TABLE claims
    ALTER COLUMN initial_estimate_amount  TYPE NUMERIC(15,2),
    ALTER COLUMN approved_estimate_amount TYPE NUMERIC(15,2),
    ALTER COLUMN deductible               TYPE NUMERIC(15,2);

-- The 'USD' default was never right for this product and currency is now required.
ALTER TABLE claims ALTER COLUMN currency DROP DEFAULT;

-- Vehicle identity (D2) and incident type (D3). No defaults on purpose: a value we did not
-- receive must not be invented, so this migration fails on a database that already holds claims.
ALTER TABLE claims
    ADD COLUMN vehicle_token VARCHAR(255) NOT NULL,
    ADD COLUMN vehicle_make  VARCHAR(100),
    ADD COLUMN vehicle_model VARCHAR(100),
    ADD COLUMN incident_type VARCHAR(30)  NOT NULL;

-- Columns the API already requires are now required by the database too.
ALTER TABLE claims
    ALTER COLUMN current_stage SET NOT NULL,
    ALTER COLUMN policy_token  SET NOT NULL,
    ALTER COLUMN garage_id     SET NOT NULL,
    ALTER COLUMN incident_at   SET NOT NULL,
    ALTER COLUMN currency      SET NOT NULL;

-- Stage values drive logic, so the database enforces them. incident_type is a taxonomy expected to
-- grow, so it is validated by the Java enum only and needs no migration to add a value.
ALTER TABLE claims
    ADD CONSTRAINT ck_claims_claim_version CHECK (claim_version >= 1),
    ADD CONSTRAINT ck_claims_currency      CHECK (currency ~ '^[A-Z]{3}$'),
    ADD CONSTRAINT ck_claims_current_stage CHECK (current_stage IN
        ('FNOL', 'ESTIMATE_RECEIVED', 'FINAL_INVOICE_RECEIVED', 'POST_SETTLEMENT')),
    ADD CONSTRAINT ck_claims_amounts_non_negative CHECK (
        COALESCE(initial_estimate_amount, 0)  >= 0 AND
        COALESCE(approved_estimate_amount, 0) >= 0 AND
        COALESCE(deductible, 0)               >= 0);

-- History matching looks claims up by vehicle within one organization.
CREATE INDEX idx_claims_org_vehicle_token ON claims (organization_id, vehicle_token);
