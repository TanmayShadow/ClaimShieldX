package com.example.ClaimShieldX.readiness;

import com.example.ClaimShieldX.claims.model.ClaimStage;
import com.example.ClaimShieldX.documents.model.DocumentType;
import com.example.ClaimShieldX.readiness.model.DocumentRequirement;
import com.example.ClaimShieldX.readiness.model.RequirementLevel;
import com.example.ClaimShieldX.readiness.repository.DocumentRequirementRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.*;

import static com.example.ClaimShieldX.documents.model.DocumentType.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The shipped checklist, checked against the table in data-contract section 6. These rows are product defaults the
 * insurer must confirm, so changing one must be a deliberate edit of both the migration data and this test.
 */
@SpringBootTest
@ActiveProfiles("test")
class RequirementSeedTests {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private DocumentRequirementRepository repository;

    private static final String R = "REQUIRED";
    private static final String O = "OPTIONAL";
    private static final String N = "NOT_APPLICABLE";

    // document type -> requirement at FNOL, ESTIMATE_RECEIVED, FINAL_INVOICE_RECEIVED for a claim of any incident type
    private static final Map<DocumentType, String[]> DEFAULTS = new EnumMap<>(DocumentType.class);

    static {
        DEFAULTS.put(CLAIM_FORM, new String[]{R, R, R});
        DEFAULTS.put(POLICY_RECORD, new String[]{R, R, R});
        DEFAULTS.put(VEHICLE_REGISTRATION, new String[]{O, O, O});
        DEFAULTS.put(DRIVING_LICENCE, new String[]{O, O, O});
        DEFAULTS.put(DAMAGE_PHOTOGRAPH, new String[]{O, O, O});
        DEFAULTS.put(GARAGE_ESTIMATE, new String[]{N, R, R});
        DEFAULTS.put(JOB_CARD, new String[]{N, O, O});
        DEFAULTS.put(SURVEYOR_REPORT, new String[]{O, O, O});
        DEFAULTS.put(FINAL_INVOICE, new String[]{N, N, R});
        DEFAULTS.put(SUPPLEMENTARY_ESTIMATE, new String[]{N, O, O});
        DEFAULTS.put(PAYMENT_PROOF, new String[]{N, N, O});
        DEFAULTS.put(FIR_POLICE_REPORT, new String[]{O, O, O});
        DEFAULTS.put(THEFT_EVIDENCE, new String[]{N, N, N});
    }

    private static final List<ClaimStage> STAGES = List.of(ClaimStage.FNOL, ClaimStage.ESTIMATE_RECEIVED, ClaimStage.FINAL_INVOICE_RECEIVED);

    private Map<String, String> rows(String incidentType) {
        Map<String, String> result = new TreeMap<>();
        jdbc.query("select stage, document_type, requirement from document_requirements where incident_type = ?",
                rs -> {
                    result.put(rs.getString("stage") + "/" + rs.getString("document_type"), rs.getString("requirement"));
                }, incidentType);
        return result;
    }

    @Test
    void theDefaultRowsMatchTheDataContractTableExactly() {
        Map<String, String> expected = new TreeMap<>();
        DEFAULTS.forEach((type, levels) -> {
            for (int i = 0; i < STAGES.size(); i++) {
                expected.put(STAGES.get(i) + "/" + type, levels[i]);
            }
        });

        assertThat(rows("ANY")).isEqualTo(expected);
    }

    @Test
    void theTheftRowsRequireTheFirAndTheftEvidenceAtEveryStageAndNothingElse() {
        Map<String, String> expected = new TreeMap<>();
        for (ClaimStage stage : STAGES) {
            expected.put(stage + "/" + FIR_POLICE_REPORT, R);
            expected.put(stage + "/" + THEFT_EVIDENCE, R);
        }

        assertThat(rows("THEFT")).isEqualTo(expected);
        assertThat(jdbc.queryForObject("select count(*) from document_requirements where incident_type not in ('ANY', 'THEFT')", Integer.class)).isZero();
    }

    @Test
    void eachStageHasExactlyOneDefiningDocument() {
        Map<String, String> gating = new TreeMap<>();
        jdbc.query("select stage, document_type from document_requirements where gating", rs -> {
            gating.put(rs.getString("stage"), rs.getString("document_type"));
        });

        assertThat(gating).containsExactlyInAnyOrderEntriesOf(Map.of(
                "FNOL", "CLAIM_FORM",
                "ESTIMATE_RECEIVED", "GARAGE_ESTIMATE",
                "FINAL_INVOICE_RECEIVED", "FINAL_INVOICE"));
    }

    @Test
    void postSettlementHasNoChecklistAndOtherIsNeverListed() {
        assertThat(jdbc.queryForObject("select count(*) from document_requirements where stage = 'POST_SETTLEMENT'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from document_requirements where document_type = 'OTHER'", Integer.class)).isZero();
    }

    @Test
    void everyRowCanBeReadBackThroughTheEntity() {
        List<DocumentRequirement> all = repository.findAll();

        assertThat(all).hasSize(45);
        assertThat(all).allSatisfy(row -> {
            assertThat(row.getStage()).isNotNull();
            assertThat(row.getDocumentType()).isNotNull();
            assertThat(row.getRequirement()).isNotNull();
            assertThat(row.getIncidentType()).isNotBlank();
        });
        assertThat(all).filteredOn(DocumentRequirement::isGating).allSatisfy(row -> assertThat(row.getRequirement()).isEqualTo(RequirementLevel.REQUIRED));
    }

    // ------------------------------------------------------------ the table protects itself

    private void insert(String stage, String incidentType, String documentType, String requirement, boolean gating) {
        jdbc.update("insert into document_requirements(stage, incident_type, document_type, requirement, gating) values (?, ?, ?, ?, ?)",
                stage, incidentType, documentType, requirement, gating);
    }

    @Test
    void theSameStageIncidentTypeAndDocumentCannotBeListedTwice() {
        assertThatThrownBy(() -> insert("FNOL", "ANY", "CLAIM_FORM", "OPTIONAL", false))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("uq_document_requirements_key");
    }

    @Test
    void anUnknownRequirementOrStageIsRefused() {
        assertThatThrownBy(() -> insert("FNOL", "SOMETHING", "CLAIM_FORM", "MANDATORY", false))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_document_requirements_requirement");
        assertThatThrownBy(() -> insert("CLOSED", "SOMETHING", "CLAIM_FORM", "REQUIRED", false))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_document_requirements_stage");
    }

    @Test
    void onlyARequiredDocumentCanDefineAStage() {
        assertThatThrownBy(() -> insert("FNOL", "SOMETHING", "JOB_CARD", "OPTIONAL", true))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_document_requirements_gating");
    }
}
