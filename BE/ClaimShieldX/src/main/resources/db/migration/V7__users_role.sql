-- Roles (decision D9). Existing users become INVESTIGATOR, which is what registration creates.
-- SYSTEM_ADMIN is deliberately absent until an endpoint needs it; adding it is a new migration.
ALTER TABLE users
    ADD COLUMN role VARCHAR(30) NOT NULL DEFAULT 'INVESTIGATOR',
    ADD CONSTRAINT ck_users_role CHECK (role IN ('INVESTIGATOR', 'ORG_ADMIN'));
