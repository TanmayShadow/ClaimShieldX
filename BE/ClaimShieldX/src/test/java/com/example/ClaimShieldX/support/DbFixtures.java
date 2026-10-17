package com.example.ClaimShieldX.support;

import com.example.ClaimShieldX.documents.model.DocumentType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Creates rows with plain SQL so database-level tests see the real constraints, not entity validation.
 * Everything created is removed by {@link #cleanUp()}; call it from an @AfterEach.
 */
public class DbFixtures {
    private final JdbcTemplate jdbc;
    private final List<UUID> organizations = new ArrayList<>();

    public DbFixtures(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public UUID organization() {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into organization(id, name, status, created_at, updated_at) values (?, ?, 'ACTIVE', now(), now())",
                id, "Org-" + id);
        organizations.add(id);
        return id;
    }

    public UUID user(UUID organizationId) {
        return user(organizationId, "INVESTIGATOR");
    }

    public UUID user(UUID organizationId, String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into users(id, email, password, organization_id, role) values (?, ?, 'not-used', ?, ?)",
                id, id + "@test.local", organizationId, role);
        return id;
    }

    public List<UUID> organizations() {
        return List.copyOf(organizations);
    }

    public UUID claim(UUID organizationId, String internalId) {
        return claim(organizationId, internalId, "FINAL_INVOICE_RECEIVED", "COLLISION");
    }

    public UUID claim(UUID organizationId, String internalId, String stage, String incidentType) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into claims(id, organization_id, internal_id, current_stage, policy_token, garage_id,
                                   vehicle_token, incident_type, incident_at, currency)
                values (?, ?, ?, ?, 'POL-1', 'G-1', 'VEH-1', ?, now(), 'INR')
                """, id, organizationId, internalId, stage, incidentType);
        return id;
    }

    /** A claim whose garage, vehicle and incident time are chosen, for tests that compare documents with the claim record. */
    public UUID claim(UUID organizationId, String internalId, String stage, String incidentType, String garageId, String vehicleToken, Instant incidentAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into claims(id, organization_id, internal_id, current_stage, policy_token, garage_id,
                                   vehicle_token, incident_type, incident_at, currency)
                values (?, ?, ?, ?, 'POL-1', ?, ?, ?, ?, 'INR')
                """, id, organizationId, internalId, stage, garageId, vehicleToken, incidentType, Timestamp.from(incidentAt));
        return id;
    }

    /** Like {@link #document}, but with a chosen processing status, arrival time and superseded flag. */
    public UUID document(UUID organizationId, UUID claimId, UUID uploadedBy, DocumentType type, String status,
                         Instant receivedAt, boolean superseded) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into documents(id, organization_id, claim_id, document_type, file_name, content_type,
                                      size_bytes, sha256, object_key, uploaded_by, status, received_at, superseded)
                values (?, ?, ?, ?, 'file.pdf', 'application/pdf', 1024, ?, ?, ?, ?, ?, ?)
                """, id, organizationId, claimId, type.name(), sha256(), "obj/" + id, uploadedBy, status,
                Timestamp.from(receivedAt), superseded);
        return id;
    }

    public UUID document(UUID organizationId, UUID claimId, UUID uploadedBy, DocumentType type, String sha256) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into documents(id, organization_id, claim_id, document_type, file_name, content_type,
                                      size_bytes, sha256, object_key, uploaded_by)
                values (?, ?, ?, ?, 'file.pdf', 'application/pdf', 1024, ?, ?, ?)
                """, id, organizationId, claimId, type.name(), sha256, "obj/" + id, uploadedBy);
        return id;
    }

    public static String sha256() {
        String hex = UUID.randomUUID().toString().replace("-", "");
        return hex + hex;
    }

    public void cleanUp() {
        for (UUID organizationId : organizations) {
            jdbc.update("delete from idempotency_keys where organization_id = ?", organizationId);
            jdbc.update("delete from document_extractions where organization_id = ?", organizationId);
            jdbc.update("delete from documents where organization_id = ?", organizationId);
            jdbc.update("delete from claims where organization_id = ?", organizationId);
            jdbc.update("delete from users where organization_id = ?", organizationId);
            jdbc.update("delete from organization where id = ?", organizationId);
        }
        organizations.clear();
    }
}
