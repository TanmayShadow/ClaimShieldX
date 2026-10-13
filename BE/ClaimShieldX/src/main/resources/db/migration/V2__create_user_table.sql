CREATE TABLE users(
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(500) NOT NULL,
    organization_id UUID NOT NULL,
    CONSTRAINT fk_user_organization
        FOREIGN KEY (organization_id)
            REFERENCES organization(id)
            ON DELETE CASCADE

);
CREATE UNIQUE INDEX idx_users_email ON users(email);
