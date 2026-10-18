package com.example.ClaimShieldX.history;

import com.example.ClaimShieldX.support.DbFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** What the database itself refuses, so a bug in the application cannot store inconsistent history. */
@SpringBootTest
@ActiveProfiles("test")
class HistoryConstraintTests {
    @Autowired private JdbcTemplate jdbc;

    private DbFixtures fixtures;
    private UUID organization;
    private UUID user;
    private UUID importId;

    @BeforeEach
    void setUp() {
        fixtures = new DbFixtures(jdbc);
        organization = fixtures.organization();
        user = fixtures.user(organization, "ORG_ADMIN");
        importId = insertImport(organization, "HISTORICAL_CLAIMS", "COMPLETED", null, 0, 0, 0, 0, true);
    }

    @AfterEach
    void cleanUp() {
        fixtures.cleanUp();
    }

    private UUID insertImport(UUID org, String type, String status, String failureCode, int total, int accepted, int rejected, int skipped, boolean finished) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into historical_imports(id, organization_id, uploaded_by, import_type, template_version, sha256, size_bytes, status, failure_code,
                                               total_rows, accepted_rows, rejected_rows, skipped_duplicate_rows, finished_at)
                values (?, ?, ?, ?, '1', ?, 100, ?, ?, ?, ?, ?, ?, ?)
                """, id, org, user, type, "a".repeat(64), status, failureCode, total, accepted, rejected, skipped, finished ? new java.sql.Timestamp(System.currentTimeMillis()) : null);
        return id;
    }

    private void insertClaim(UUID org, UUID imp, String source, String external, int version, String currency, String amount, String hash) {
        jdbc.update("""
                insert into historical_claims(id, organization_id, import_id, row_number, source_record_id, external_claim_id, claim_version, vehicle_token,
                                              incident_at, incident_at_precision, claim_type, claim_status, claimed_amount, currency, content_hash)
                values (?, ?, ?, 1, ?, ?, ?, 'VEH-1', now(), 'DATETIME', 'COLLISION', 'CLOSED', ?::numeric, ?, ?)
                """, UUID.randomUUID(), org, imp, source, external, version, amount, currency, hash);
    }

    private void insertGarage(UUID org, UUID imp, String source, String start, String end, int count, Integer variance, String currency) {
        jdbc.update("""
                insert into garage_history_records(id, organization_id, import_id, row_number, source_record_id, garage_id, reporting_period_start,
                                                   reporting_period_end, claim_count, estimate_invoice_variance_count, currency, content_hash)
                values (?, ?, ?, 1, ?, 'G-1', ?::date, ?::date, ?, ?, ?, ?)
                """, UUID.randomUUID(), org, imp, source, start, end, count, variance, currency, "b".repeat(64));
    }

    // ------------------------------------------------------------ imports

    @Test
    void everyRowOfAnImportMustBeAccountedForExactlyOnce() {
        assertThatThrownBy(() -> insertImport(organization, "HISTORICAL_CLAIMS", "COMPLETED", null, 5, 2, 1, 1, true))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_historical_imports_counts");
        assertThatCode(() -> insertImport(organization, "HISTORICAL_CLAIMS", "COMPLETED_WITH_ERRORS", null, 5, 2, 2, 1, true)).doesNotThrowAnyException();
        assertThatThrownBy(() -> insertImport(organization, "HISTORICAL_CLAIMS", "COMPLETED", null, -1, -1, 0, 0, true))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("historical_imports");
    }

    @Test
    void aFailedImportHasACodeAndNoRowsAndOnlyAFailedImportHasACode() {
        assertThatCode(() -> insertImport(organization, "HISTORICAL_CLAIMS", "FAILED", "INVALID_HEADER", 0, 0, 0, 0, true)).doesNotThrowAnyException();
        assertThatThrownBy(() -> insertImport(organization, "HISTORICAL_CLAIMS", "FAILED", null, 0, 0, 0, 0, true))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_historical_imports_failure");
        assertThatThrownBy(() -> insertImport(organization, "HISTORICAL_CLAIMS", "COMPLETED", "INVALID_HEADER", 0, 0, 0, 0, true))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_historical_imports_failure");
        assertThatThrownBy(() -> insertImport(organization, "HISTORICAL_CLAIMS", "FAILED", "INVALID_HEADER", 3, 3, 0, 0, true))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_historical_imports_failed_empty");
    }

    @Test
    void onlyAFinishedImportHasAFinishTime() {
        assertThatThrownBy(() -> insertImport(organization, "HISTORICAL_CLAIMS", "COMPLETED", null, 0, 0, 0, 0, false))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_historical_imports_finished");
        assertThatThrownBy(() -> insertImport(organization, "HISTORICAL_CLAIMS", "PROCESSING", null, 0, 0, 0, 0, true))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_historical_imports_finished");
        assertThatCode(() -> insertImport(organization, "HISTORICAL_CLAIMS", "PROCESSING", null, 0, 0, 0, 0, false)).doesNotThrowAnyException();
    }

    @Test
    void theTypeAndStatusMustBeKnown() {
        assertThatThrownBy(() -> insertImport(organization, "SOMETHING", "COMPLETED", null, 0, 0, 0, 0, true))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_historical_imports_type");
        assertThatThrownBy(() -> insertImport(organization, "HISTORICAL_CLAIMS", "DONE", null, 0, 0, 0, 0, false))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_historical_imports_status");
    }

    // ------------------------------------------------------------ row results

    private void insertRow(UUID org, UUID imp, int number, String status, String errors) {
        jdbc.update("insert into historical_import_rows(id, organization_id, import_id, row_number, status, errors) values (?, ?, ?, ?, ?, ?::jsonb)",
                UUID.randomUUID(), org, imp, number, status, errors);
    }

    @Test
    void aRejectedRowAlwaysSaysWhyAndOtherRowsCarryNoErrors() {
        assertThatCode(() -> insertRow(organization, importId, 1, "REJECTED", "[{\"code\":\"INVALID_DATE\"}]")).doesNotThrowAnyException();
        assertThatThrownBy(() -> insertRow(organization, importId, 2, "REJECTED", null)).isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_historical_import_rows_errors");
        assertThatThrownBy(() -> insertRow(organization, importId, 3, "REJECTED", "[]")).isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_historical_import_rows_errors");
        assertThatThrownBy(() -> insertRow(organization, importId, 4, "REJECTED", "{\"code\":\"x\"}")).isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_historical_import_rows_errors");
        assertThatThrownBy(() -> insertRow(organization, importId, 5, "ACCEPTED", "[{\"code\":\"x\"}]")).isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_historical_import_rows_errors");
        assertThatCode(() -> insertRow(organization, importId, 6, "ACCEPTED", null)).doesNotThrowAnyException();
        assertThatCode(() -> insertRow(organization, importId, 7, "SKIPPED_DUPLICATE", null)).doesNotThrowAnyException();
    }

    @Test
    void aRowNumberIsUniquePerImportAndPositive() {
        insertRow(organization, importId, 1, "ACCEPTED", null);

        assertThatThrownBy(() -> insertRow(organization, importId, 1, "ACCEPTED", null)).isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("uq_historical_import_rows_number");
        assertThatThrownBy(() -> insertRow(organization, importId, 0, "ACCEPTED", null)).isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_historical_import_rows_number");
    }

    @Test
    void rowsAndRecordsMustBelongToAnImportOfTheirOwnOrganization() {
        UUID other = fixtures.organization();

        assertThatThrownBy(() -> insertRow(other, importId, 1, "ACCEPTED", null)).isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("fk_historical_import_rows_import");
        assertThatThrownBy(() -> insertClaim(other, importId, "S1", "E1", 1, "INR", "1", "a".repeat(64))).isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("fk_historical_claims_import");
        assertThatThrownBy(() -> insertGarage(other, importId, "S1", "2025-01-01", "2025-02-01", 5, 1, "INR")).isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("fk_garage_history_import");
    }

    // ------------------------------------------------------------ historical claims

    @Test
    void aRecordIdIsUniquePerOrganizationAndAClaimVersionIsUniquePerClaim() {
        insertClaim(organization, importId, "S1", "E1", 1, "INR", "10", "a".repeat(64));

        assertThatThrownBy(() -> insertClaim(organization, importId, "S1", "E2", 1, "INR", "10", "a".repeat(64)))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("uq_historical_claims_source");
        assertThatThrownBy(() -> insertClaim(organization, importId, "S2", "E1", 1, "INR", "10", "a".repeat(64)))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("uq_historical_claims_version");
        assertThatCode(() -> insertClaim(organization, importId, "S3", "E1", 2, "INR", "10", "a".repeat(64))).doesNotThrowAnyException();
    }

    @Test
    void theSameRecordIdMayExistInAnotherOrganization() {
        UUID other = fixtures.organization();
        UUID otherUser = fixtures.user(other, "ORG_ADMIN");
        UUID otherImport = UUID.randomUUID();
        jdbc.update("""
                insert into historical_imports(id, organization_id, uploaded_by, import_type, template_version, sha256, size_bytes, status, finished_at)
                values (?, ?, ?, 'HISTORICAL_CLAIMS', '1', ?, 1, 'COMPLETED', now())
                """, otherImport, other, otherUser, "c".repeat(64));
        insertClaim(organization, importId, "S1", "E1", 1, "INR", "10", "a".repeat(64));

        assertThatCode(() -> insertClaim(other, otherImport, "S1", "E1", 1, "INR", "10", "a".repeat(64))).doesNotThrowAnyException();
    }

    @Test
    void claimValuesAreRangeAndFormatChecked() {
        assertThatThrownBy(() -> insertClaim(organization, importId, "S1", "E1", 0, "INR", "10", "a".repeat(64))).hasMessageContaining("ck_historical_claims_version");
        assertThatThrownBy(() -> insertClaim(organization, importId, "S2", "E2", 1, "inr", "10", "a".repeat(64))).hasMessageContaining("ck_historical_claims_currency");
        assertThatThrownBy(() -> insertClaim(organization, importId, "S3", "E3", 1, "INR", "-1", "a".repeat(64))).hasMessageContaining("ck_historical_claims_amounts");
        assertThatThrownBy(() -> insertClaim(organization, importId, "S4", "E4", 1, "INR", "10", "short")).hasMessageContaining("ck_historical_claims_hash");
        assertThatThrownBy(() -> insertClaim(organization, importId, "S5", "E5", 1, "INR", "123456789012345.00", "a".repeat(64))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void damageAreaCodesMustBeAnArray() {
        insertClaim(organization, importId, "S1", "E1", 1, "INR", "10", "a".repeat(64));

        assertThatThrownBy(() -> jdbc.update("update historical_claims set damage_area_codes = '{\"a\":1}'::jsonb where organization_id = ?", organization))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_historical_claims_codes");
        assertThatCode(() -> jdbc.update("update historical_claims set damage_area_codes = '[\"A\",\"B\"]'::jsonb where organization_id = ?", organization)).doesNotThrowAnyException();
    }

    // ------------------------------------------------------------ garage history

    @Test
    void aGaragePeriodCannotRunBackwardsAndCountsCannotExceedTheClaimCount() {
        assertThatThrownBy(() -> insertGarage(organization, importId, "G1", "2025-02-01", "2025-01-01", 5, 1, "INR")).hasMessageContaining("ck_garage_history_period");
        assertThatThrownBy(() -> insertGarage(organization, importId, "G2", "2025-01-01", "2025-02-01", 5, 6, "INR")).hasMessageContaining("ck_garage_history_counts");
        assertThatThrownBy(() -> insertGarage(organization, importId, "G3", "2025-01-01", "2025-02-01", -1, null, "INR")).hasMessageContaining("ck_garage_history_counts");
        assertThatThrownBy(() -> insertGarage(organization, importId, "G4", "2025-01-01", "2025-02-01", 5, 1, "rs")).hasMessageContaining("ck_garage_history_currency");
        assertThatCode(() -> insertGarage(organization, importId, "G5", "2025-01-01", "2025-01-01", 5, 5, "INR")).doesNotThrowAnyException();
    }

    @Test
    void aGarageRecordIdIsUniquePerOrganization() {
        insertGarage(organization, importId, "G1", "2025-01-01", "2025-02-01", 5, 1, "INR");

        assertThatThrownBy(() -> insertGarage(organization, importId, "G1", "2025-03-01", "2025-04-01", 5, 1, "INR"))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("uq_garage_history_source");
    }

    @Test
    void theTablesCarryNoFieldForRawPersonalDataByDesign() {
        var columns = jdbc.queryForList("select column_name from information_schema.columns where table_name in ('historical_claims', 'garage_history_records', 'historical_imports')", String.class);

        assertThat(columns).isNotEmpty().noneMatch(c -> List.of("name", "registration", "phone", "email", "address", "owner", "licence").stream().anyMatch(c::contains));
    }
}
