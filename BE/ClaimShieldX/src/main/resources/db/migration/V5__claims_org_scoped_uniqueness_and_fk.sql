-- Duplicate claim ids are per organization, and must be enforced by the database, not only by the service.
ALTER TABLE claims ALTER COLUMN internal_id SET NOT NULL;

ALTER TABLE claims
    ADD CONSTRAINT uq_claims_org_internal_id UNIQUE (organization_id, internal_id);

-- Default NO ACTION: deleting an organization must not silently delete its claims.
ALTER TABLE claims
    ADD CONSTRAINT fk_claims_organization
        FOREIGN KEY (organization_id) REFERENCES organization(id);
