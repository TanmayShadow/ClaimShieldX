package com.example.ClaimShieldX.db;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs the real migrations against brand-new throwaway databases, so a fresh install and an upgrade of an
 * existing database are both proven on every build. Needs the same server and credentials as the other tests.
 */
@SpringBootTest
@ActiveProfiles("test")
class MigrationTests {
    @Autowired private Environment environment;

    private interface Body {
        void run(String url, String user, String password) throws Exception;
    }

    private void withTempDatabase(Body body) throws Exception {
        String url = environment.getRequiredProperty("spring.datasource.url");
        String user = environment.getRequiredProperty("spring.datasource.username");
        String password = environment.getRequiredProperty("spring.datasource.password");
        String serverUrl = url.substring(0, url.lastIndexOf('/'));
        String name = "claimshieldx_mig_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        try (Connection admin = DriverManager.getConnection(serverUrl + "/postgres", user, password);
             Statement statement = admin.createStatement()) {
            statement.execute("CREATE DATABASE " + name);
        }
        try {
            body.run(serverUrl + "/" + name, user, password);
        } finally {
            try (Connection admin = DriverManager.getConnection(serverUrl + "/postgres", user, password);
                 Statement statement = admin.createStatement()) {
                statement.execute("DROP DATABASE IF EXISTS " + name + " WITH (FORCE)");
            }
        }
    }

    private Flyway flyway(String url, String user, String password, String target) {
        return Flyway.configure()
                .dataSource(url, user, password)
                .locations("classpath:db/migration")
                .cleanDisabled(true)
                .target(target)
                .load();
    }

    private String scalar(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    @Test
    void aFreshDatabaseMigratesToTheLatestVersion() throws Exception {
        withTempDatabase((url, user, password) -> {
            Flyway flyway = flyway(url, user, password, "latest");
            flyway.migrate();

            assertThat(flyway.info().pending()).isEmpty();
            try (Connection connection = DriverManager.getConnection(url, user, password)) {
                assertThat(scalar(connection, "select count(*) from information_schema.tables where table_schema = 'public' and table_name in ('documents', 'idempotency_keys')")).isEqualTo("2");
                assertThat(scalar(connection, "select numeric_precision || ',' || numeric_scale from information_schema.columns where table_name = 'claims' and column_name = 'initial_estimate_amount'")).isEqualTo("15,2");
                assertThat(scalar(connection, "select count(*) from information_schema.columns where table_name = 'claims' and column_name in ('vehicle_token', 'vehicle_make', 'vehicle_model', 'incident_type')")).isEqualTo("4");
                assertThat(scalar(connection, "select column_default from information_schema.columns where table_name = 'users' and column_name = 'role'")).contains("INVESTIGATOR");
                // 3 stages x 13 document types, plus 6 theft overrides.
                assertThat(scalar(connection, "select count(*) from document_requirements")).isEqualTo("45");
                assertThat(scalar(connection, "select data_type from information_schema.columns where table_name = 'document_extractions' and column_name = 'result'")).isEqualTo("jsonb");
                assertThat(scalar(connection, "select count(*) from information_schema.tables where table_schema = 'public' and table_name in "
                        + "('historical_imports', 'historical_import_rows', 'historical_claims', 'garage_history_records')")).isEqualTo("4");
                assertThat(scalar(connection, "select count(*) from pg_constraint where conname in ('uq_historical_claims_source', 'uq_historical_claims_version', 'uq_garage_history_source')")).isEqualTo("3");
                assertThat(scalar(connection, "select count(*) from information_schema.tables where table_schema = 'public' and table_name in ('rule_runs', 'rule_results')")).isEqualTo("2");
                assertThat(scalar(connection, "select count(*) from pg_constraint where conname in ('uq_rule_runs_inputs', 'uq_rule_results_run_rule', 'fk_rule_results_run', 'ck_rule_results_severity_with_flag')")).isEqualTo("4");
                assertThat(scalar(connection, "select count(*) from pg_indexes where indexname in ('idx_documents_organization_sha256', 'idx_document_extractions_invoices', 'idx_rule_runs_claim_created')")).isEqualTo("3");
                assertThat(scalar(connection, "select data_type from information_schema.columns where table_name = 'rule_results' and column_name = 'result_values'")).isEqualTo("jsonb");
                assertThat(scalar(connection, "select count(*) from information_schema.tables where table_schema = 'public' and table_name in ('investigations', 'investigation_stages')")).isEqualTo("2");
                assertThat(scalar(connection, "select count(*) from pg_indexes where indexname in ('uq_investigations_one_active_per_claim', 'idx_investigations_claimable', 'idx_investigations_organization_created')")).isEqualTo("3");
                assertThat(scalar(connection, "select count(*) from pg_constraint where conname in ('fk_investigations_rule_run', 'ck_investigations_lease', 'ck_investigations_terminal_lease', 'uq_investigation_stages_stage')")).isEqualTo("4");
                assertThat(scalar(connection, "select count(*) from information_schema.tables where table_schema = 'public' and table_name in "
                        + "('evidence_snapshots', 'evidence_items', 'investigation_analyses', 'findings', 'reports', 'review_actions', 'audit_events')")).isEqualTo("7");
                assertThat(scalar(connection, "select count(*) from pg_constraint where conname in ('uq_evidence_snapshots_investigation', 'uq_evidence_items_ref', 'uq_findings_ordinal', "
                        + "'uq_reports_investigation', 'uq_reports_claim_version', 'ck_reports_score', 'ck_findings_rejection', 'ck_review_actions_rationale', 'ck_investigation_analyses_error')")).isEqualTo("9");
            }
        });
    }

    @Test
    void anExistingDatabaseWithoutClaimsUpgradesAndKeepsItsUsers() throws Exception {
        withTempDatabase((url, user, password) -> {
            flyway(url, user, password, "4").migrate();
            try (Connection connection = DriverManager.getConnection(url, user, password); Statement statement = connection.createStatement()) {
                statement.execute("insert into organization(id, name, status, created_at, updated_at) values ('11111111-1111-1111-1111-111111111111', 'Org', 'ACTIVE', now(), now())");
                statement.execute("insert into users(id, email, password, organization_id) values ('22222222-2222-2222-2222-222222222222', 'old@test.local', 'x', '11111111-1111-1111-1111-111111111111')");
            }

            Flyway flyway = flyway(url, user, password, "latest");
            flyway.migrate();

            assertThat(flyway.info().pending()).isEmpty();
            try (Connection connection = DriverManager.getConnection(url, user, password)) {
                assertThat(scalar(connection, "select role from users where email = 'old@test.local'")).isEqualTo("INVESTIGATOR");
                assertThat(scalar(connection, "select count(*) from pg_constraint where conname = 'uq_claims_org_internal_id'")).isEqualTo("1");
                assertThat(scalar(connection, "select count(*) from document_requirements")).isEqualTo("45");
            }
        });
    }

    @Test
    void anExistingClaimWithoutAVehicleTokenBlocksTheUpgradeAndLeavesTheDatabaseUntouched() throws Exception {
        withTempDatabase((url, user, password) -> {
            flyway(url, user, password, "5").migrate();
            try (Connection connection = DriverManager.getConnection(url, user, password); Statement statement = connection.createStatement()) {
                statement.execute("insert into organization(id, name, status, created_at, updated_at) values ('11111111-1111-1111-1111-111111111111', 'Org', 'ACTIVE', now(), now())");
                statement.execute("insert into claims(id, organization_id, internal_id) values ('33333333-3333-3333-3333-333333333333', '11111111-1111-1111-1111-111111111111', 'OLD-1')");
            }

            Flyway flyway = flyway(url, user, password, "latest");
            assertThatThrownBy(flyway::migrate).isInstanceOf(FlywayException.class);

            try (Connection connection = DriverManager.getConnection(url, user, password)) {
                assertThat(scalar(connection, "select max(version::int) from flyway_schema_history where success")).isEqualTo("5");
                assertThat(scalar(connection, "select count(*) from information_schema.columns where table_name = 'claims' and column_name = 'vehicle_token'")).isEqualTo("0");
                assertThat(scalar(connection, "select data_type from information_schema.columns where table_name = 'claims' and column_name = 'initial_estimate_amount'")).isEqualTo("bigint");
                assertThat(scalar(connection, "select count(*) from claims")).isEqualTo("1");
            }
        });
    }
}
