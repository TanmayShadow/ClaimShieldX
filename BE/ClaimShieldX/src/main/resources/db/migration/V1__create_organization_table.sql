CREATE TABLE organization(
    id UUID PRIMARY KEY,
    name varchar(255) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT uq_organization_name UNIQUE (name)
);
CREATE INDEX idx_organization_name ON organization(name);