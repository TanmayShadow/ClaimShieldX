package com.example.ClaimShieldX.rules;

import com.example.ClaimShieldX.documents.model.DocumentStatus;
import com.example.ClaimShieldX.documents.model.DocumentType;
import com.example.ClaimShieldX.history.HistoryCsv;
import com.example.ClaimShieldX.rules.engine.Inputs;
import com.example.ClaimShieldX.support.TestActors;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Rules that look beyond one claim: the same file, the same invoice, and the imported history, always inside one organization. */
class RuleCrossClaimApiTests extends RuleApiTestBase {

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> matches(String run, String ruleId) {
        Map<String, Object> values = (Map<String, Object>) result(run, ruleId).get("values");
        return (List<Map<String, Object>>) values.get("matches");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> firstEvidence(String run, String ruleId) {
        return ((List<Map<String, Object>>) result(run, ruleId).get("evidence")).get(0);
    }

    // ---------------------------------------------------------------- invoice identity (reads stored extractions with a JSON query)

    @Test
    void theSameInvoiceNumberIssuerAmountAndDateOnAnotherClaimOfTheOrganizationIsFlagged() throws Exception {
        TestActors actor = investigator();
        UUID mine = cleanClaim(actor, "CLM-MINE");
        UUID other = ruleClaim(actor, "CLM-OTHER");
        attach(actor, other, Inputs.invoice());

        String run = runOk(actor, mine);

        assertThat(outcome(run, "INVOICE_IDENTITY_DUPLICATE")).isEqualTo("FLAG");
        assertThat(result(run, "INVOICE_IDENTITY_DUPLICATE").get("severity")).isEqualTo("REVIEW");
        assertThat(matches(run, "INVOICE_IDENTITY_DUPLICATE")).singleElement().satisfies(m -> {
            assertThat(m).containsEntry("level", "SAME_NUMBER_ISSUER_AMOUNT_AND_DATE").containsEntry("claimInternalId", "CLM-OTHER").containsEntry("location", "ANOTHER_CLAIM");
            assertThat(m).containsEntry("sameVehicleToken", true);
        });
        assertThat(JsonPath.<List<Object>>read(run, "$.inputs.sameNumberInvoiceDocumentIds")).hasSize(1);
    }

    @Test
    void anInvoiceThatPrintsNoGarageIdentifierIsComparedByTheGarageRecordedOnItsClaim() throws Exception {
        TestActors actor = investigator();
        UUID mine = ruleClaim(actor, "CLM-MINE");
        attach(actor, mine, Inputs.invoice().absent("garageIdentifier"));
        attach(actor, mine, Inputs.estimate());
        UUID same = ruleClaim(actor, "CLM-SAME-GARAGE");
        attach(actor, same, Inputs.invoice().absent("garageIdentifier"));
        UUID otherGarage = ruleClaim(actor, "CLM-OTHER-GARAGE", "FINAL_INVOICE_RECEIVED", "COLLISION", "VEH-1", "GAR-ELSEWHERE");
        attach(actor, otherGarage, Inputs.invoice().absent("garageIdentifier"));

        String run = runOk(actor, mine);

        assertThat(outcome(run, "INVOICE_IDENTITY_DUPLICATE")).isEqualTo("FLAG");
        assertThat(matches(run, "INVOICE_IDENTITY_DUPLICATE")).singleElement().satisfies(m ->
                assertThat(m).containsEntry("claimInternalId", "CLM-SAME-GARAGE").containsEntry("issuerBasis", "CLAIM_RECORD_GARAGE"));
        assertThat(((Map<?, ?>) result(run, "INVOICE_IDENTITY_DUPLICATE").get("values")).get("sameNumberFromOtherIssuers")).isEqualTo(1);
    }

    @Test
    void punctuationCaseAndSpacingInTheInvoiceNumberDoNotHideTheRepeat() throws Exception {
        TestActors actor = investigator();
        UUID mine = cleanClaim(actor, "CLM-MINE");
        UUID other = ruleClaim(actor, "CLM-OTHER");
        attach(actor, other, Inputs.invoice().set("documentNumber", "inv 2026/0042"));

        assertThat(outcome(runOk(actor, mine), "INVOICE_IDENTITY_DUPLICATE")).isEqualTo("FLAG");
    }

    @Test
    void aDifferentIssuerWithTheSameNumberIsNotAMatchButIsCounted() throws Exception {
        TestActors actor = investigator();
        UUID mine = cleanClaim(actor, "CLM-MINE");
        UUID other = ruleClaim(actor, "CLM-OTHER");
        attach(actor, other, Inputs.invoice().set("garageIdentifier", "GAR-ELSEWHERE"));

        String run = runOk(actor, mine);

        assertThat(outcome(run, "INVOICE_IDENTITY_DUPLICATE")).isEqualTo("PASS");
        assertThat(((Map<?, ?>) result(run, "INVOICE_IDENTITY_DUPLICATE").get("values")).get("sameNumberFromOtherIssuers")).isEqualTo(1);
    }

    @Test
    void aDifferentAmountIsRecordedAsPossiblyACorrectedInvoiceNotAsMaterial() throws Exception {
        TestActors actor = investigator();
        UUID mine = cleanClaim(actor, "CLM-MINE");
        UUID other = ruleClaim(actor, "CLM-OTHER");
        attach(actor, other, Inputs.invoice().set("grandTotal", "14000.00"));

        String run = runOk(actor, mine);

        assertThat(outcome(run, "INVOICE_IDENTITY_DUPLICATE")).isEqualTo("FLAG");
        assertThat(result(run, "INVOICE_IDENTITY_DUPLICATE").get("severity")).isEqualTo("INFO");
    }

    @Test
    void anInvoiceOfAnotherOrganizationIsNeverSeen() throws Exception {
        TestActors actor = investigator();
        TestActors stranger = investigator();
        UUID mine = cleanClaim(actor, "CLM-MINE");
        UUID theirs = ruleClaim(stranger, "CLM-THEIRS");
        attach(stranger, theirs, Inputs.invoice().sha(Inputs.invoice().sha256));

        String run = runOk(actor, mine);

        assertThat(outcome(run, "INVOICE_IDENTITY_DUPLICATE")).isEqualTo("PASS");
        assertThat(JsonPath.<List<Object>>read(run, "$.inputs.sameNumberInvoiceDocumentIds")).isEmpty();
    }

    @Test
    void onlyTheNewestSuccessfulExtractionOfAnotherInvoiceCounts() throws Exception {
        TestActors actor = investigator();
        UUID mine = cleanClaim(actor, "CLM-MINE");
        UUID other = ruleClaim(actor, "CLM-OTHER");
        UUID document = attach(actor, other, Inputs.invoice());
        jdbc.update("""
                insert into document_extractions(id, organization_id, claim_id, document_id, document_type, attempt, status, schema_version, prompt_version, model_id,
                                                 failure_code, model_calls, duration_ms)
                values (?, ?, ?, ?, 'FINAL_INVOICE', 2, 'FAILED', 'itemized-cost-2', '1', 'test-model', 'MODEL_UNAVAILABLE', 1, 10)
                """, UUID.randomUUID(), actor.organizationId(), other, document);

        assertThat(outcome(runOk(actor, mine), "INVOICE_IDENTITY_DUPLICATE")).isEqualTo("PASS");
    }

    @Test
    void anEarlierVersionOfTheInvoiceReplacedOnTheSameClaimIsTheExpectedReissue() throws Exception {
        TestActors actor = investigator();
        UUID claim = ruleClaim(actor, "CLM-REISSUED");
        attach(actor, claim, Inputs.invoice().received(Inputs.T0.minusSeconds(86_400)).replaced());
        attach(actor, claim, Inputs.invoice().set("grandTotal", "15930.00"));

        String run = runOk(actor, claim);

        assertThat(outcome(run, "INVOICE_IDENTITY_DUPLICATE")).isEqualTo("FLAG");
        assertThat(result(run, "INVOICE_IDENTITY_DUPLICATE").get("severity")).isEqualTo("INFO");
        assertThat(matches(run, "INVOICE_IDENTITY_DUPLICATE").get(0)).containsEntry("location", "EARLIER_VERSION_ON_THIS_CLAIM");
    }

    @Test
    void atMostFiftyCandidateInvoicesAreConsidered() throws Exception {
        TestActors actor = investigator();
        UUID mine = cleanClaim(actor, "CLM-MINE");
        for (int i = 0; i < 53; i++) {
            attach(actor, ruleClaim(actor, "CLM-SAME-" + i), Inputs.invoice());
        }

        String run = runOk(actor, mine);

        assertThat(JsonPath.<List<Object>>read(run, "$.inputs.sameNumberInvoiceDocumentIds")).hasSize(50);
    }

    // ---------------------------------------------------------------- exact file duplicate (reads documents by checksum)

    @Test
    void theSameBytesFiledUnderAnotherTypeOnTheSameClaimAreFlagged() throws Exception {
        TestActors actor = investigator();
        UUID claim = ruleClaim(actor, "CLM-TWICE");
        String sha = Inputs.invoice().sha256;
        attach(actor, claim, Inputs.invoice().sha(sha));
        attach(actor, claim, Inputs.estimate().sha(sha));

        String run = runOk(actor, claim);

        assertThat(outcome(run, "DOCUMENT_FILE_DUPLICATE")).isEqualTo("FLAG");
        assertThat(matches(run, "DOCUMENT_FILE_DUPLICATE")).extracting(m -> m.get("relation")).containsOnly("SAME_FILE_UNDER_ANOTHER_TYPE_ON_THIS_CLAIM");
    }

    @Test
    void aReplacedEarlierVersionOfTheSameFileOnThisClaimIsInformationOnly() throws Exception {
        TestActors actor = investigator();
        UUID claim = ruleClaim(actor, "CLM-RESTORED");
        String sha = Inputs.invoice().sha256;
        attach(actor, claim, Inputs.invoice().sha(sha).replaced().received(Inputs.T0.minusSeconds(3600)));
        attach(actor, claim, Inputs.invoice().sha(sha).set("documentNumber", "INV-2026-0099"));

        String run = runOk(actor, claim);

        assertThat(outcome(run, "DOCUMENT_FILE_DUPLICATE")).isEqualTo("FLAG");
        assertThat(result(run, "DOCUMENT_FILE_DUPLICATE").get("severity")).isEqualTo("INFO");
    }

    @Test
    void theSameBytesInAnotherOrganizationAreNeverSeen() throws Exception {
        TestActors actor = investigator();
        TestActors stranger = investigator();
        UUID mine = ruleClaim(actor, "CLM-MINE");
        String sha = Inputs.invoice().sha256;
        attach(actor, mine, Inputs.invoice().sha(sha));
        attach(stranger, ruleClaim(stranger, "CLM-THEIRS"), Inputs.invoice().sha(sha));

        assertThat(outcome(runOk(actor, mine), "DOCUMENT_FILE_DUPLICATE")).isEqualTo("PASS");
    }

    @Test
    void aSharedPolicyRecordBetweenClaimsOfTheSamePolicyIsExpected() throws Exception {
        TestActors actor = investigator();
        UUID mine = cleanClaim(actor, "CLM-MINE");
        String sha = DocumentFixtures.sha();
        attach(actor, mine, Inputs.plain(DocumentType.POLICY_RECORD).status(DocumentStatus.RECEIVED).sha(sha));
        attach(actor, ruleClaim(actor, "CLM-SECOND"), Inputs.plain(DocumentType.POLICY_RECORD).status(DocumentStatus.RECEIVED).sha(sha));

        String run = runOk(actor, mine);

        assertThat(outcome(run, "DOCUMENT_FILE_DUPLICATE")).isEqualTo("FLAG");
        assertThat(result(run, "DOCUMENT_FILE_DUPLICATE").get("severity")).isEqualTo("INFO");
        assertThat(matches(run, "DOCUMENT_FILE_DUPLICATE").get(0)).containsEntry("relation", "REFERENCE_DOCUMENT_SHARED_WITH_ANOTHER_CLAIM").containsEntry("samePolicyToken", true);
    }

    // ---------------------------------------------------------------- history (reads the imports of B6)

    @Test
    void anEarlierClaimOnTheSameVehicleInTheImportedHistoryIsRecordedWithItsSource() throws Exception {
        TestActors actor = investigator();
        TestActors admin = adminOf(actor);
        UUID claim = cleanClaim(actor, "CLM-WITH-HISTORY");
        importClaimsOk(admin, HistoryCsv.claims(HistoryCsv.claim("1").vehicle("VEH-1").policy("POL-OTHER").garage("GAR-7").on("2026-07-01")));

        String run = runOk(actor, claim);

        assertThat(outcome(run, "HISTORICAL_RELATIONSHIP")).isEqualTo("FLAG");
        assertThat(result(run, "HISTORICAL_RELATIONSHIP").get("severity")).isEqualTo("REVIEW");
        assertThat(firstEvidence(run, "HISTORICAL_RELATIONSHIP")).containsEntry("kind", "HISTORICAL_RECORD").containsEntry("sourceRecordId", "HC-1").containsEntry("rowNumber", 1);
        assertThat(JsonPath.<String>read(run, "$.inputs.history.status")).isEqualTo("MATCHES_FOUND");
    }

    @Test
    void historyThatExistsButDoesNotRelateToTheClaimPasses() throws Exception {
        TestActors actor = investigator();
        TestActors admin = adminOf(actor);
        UUID claim = cleanClaim(actor, "CLM-UNRELATED");
        importClaimsOk(admin, HistoryCsv.claims(HistoryCsv.claim("1").vehicle("VEH-ELSEWHERE").policy("POL-ELSEWHERE").on("2026-07-01")));

        assertThat(outcome(runOk(actor, claim), "HISTORICAL_RELATIONSHIP")).isEqualTo("PASS");
    }

    @Test
    void sharingOnlyTheGarageWithManyHistoricalClaimsIsNotARelationship() throws Exception {
        TestActors actor = investigator();
        TestActors admin = adminOf(actor);
        UUID claim = cleanClaim(actor, "CLM-BUSY-GARAGE");
        HistoryCsv.ClaimRow[] rows = new HistoryCsv.ClaimRow[15];
        for (int i = 0; i < rows.length; i++) {
            rows[i] = HistoryCsv.claim("G" + i).vehicle("VEH-OTHER-" + i).policy("POL-OTHER-" + i).garage("GAR-1").on("2026-0" + (1 + i % 8) + "-1" + (i % 9));
        }
        importClaimsOk(admin, HistoryCsv.claims(rows));

        assertThat(outcome(runOk(actor, claim), "HISTORICAL_RELATIONSHIP")).isEqualTo("PASS");
    }

    @Test
    void aSameVehicleClaimOlderThanTheWindowIsRecordedButNotMaterial() throws Exception {
        TestActors actor = investigator();
        TestActors admin = adminOf(actor);
        UUID claim = cleanClaim(actor, "CLM-OLD-HISTORY");
        importClaimsOk(admin, HistoryCsv.claims(HistoryCsv.claim("1").vehicle("VEH-1").policy("POL-OTHER").on("2024-05-02"), HistoryCsv.claim("2").vehicle("VEH-1").policy("POL-OTHER").on("2024-11-20")));

        String run = runOk(actor, claim);

        assertThat(outcome(run, "HISTORICAL_RELATIONSHIP")).isEqualTo("FLAG");
        assertThat(result(run, "HISTORICAL_RELATIONSHIP").get("severity")).isEqualTo("INFO");
    }

    @Test
    void newlyImportedHistoryGivesANewRun() throws Exception {
        TestActors actor = investigator();
        TestActors admin = adminOf(actor);
        UUID claim = cleanClaim(actor, "CLM-LATER-HISTORY");
        String before = runOk(actor, claim);
        assertThat(outcome(before, "HISTORICAL_RELATIONSHIP")).isEqualTo("INCOMPLETE");

        importClaimsOk(admin, HistoryCsv.claims(HistoryCsv.claim("1").vehicle("VEH-1").policy("POL-OTHER").on("2026-07-01")));
        runRules(actor, claim).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated());

        String after = body(latestRun(actor, claim));
        assertThat(JsonPath.<String>read(after, "$.id")).isNotEqualTo(JsonPath.<String>read(before, "$.id"));
        assertThat(outcome(after, "HISTORICAL_RELATIONSHIP")).isEqualTo("FLAG");
    }

    @Test
    void anotherOrganizationsHistoryIsNeverUsed() throws Exception {
        TestActors actor = investigator();
        TestActors stranger = investigator();
        UUID claim = cleanClaim(actor, "CLM-ISOLATED");
        importClaimsOk(adminOf(stranger), HistoryCsv.claims(HistoryCsv.claim("1").vehicle("VEH-1").policy("POL-1").on("2026-07-01")));

        assertThat(outcome(runOk(actor, claim), "HISTORICAL_RELATIONSHIP")).isEqualTo("INCOMPLETE");
    }

    /** Local helper so the test file does not depend on another test's internals. */
    private static final class DocumentFixtures {
        static String sha() {
            return com.example.ClaimShieldX.support.DbFixtures.sha256();
        }
    }
}
