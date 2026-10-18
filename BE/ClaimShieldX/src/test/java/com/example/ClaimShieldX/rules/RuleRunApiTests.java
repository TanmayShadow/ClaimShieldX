package com.example.ClaimShieldX.rules;

import com.example.ClaimShieldX.documents.model.DocumentStatus;
import com.example.ClaimShieldX.documents.model.DocumentType;
import com.example.ClaimShieldX.rules.engine.Inputs;
import com.example.ClaimShieldX.support.TestActors;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RuleRunApiTests extends RuleApiTestBase {

    @Test
    void aCleanClaimIsEvaluatedByEveryRuleAndStoredAsANewRun() throws Exception {
        TestActors actor = investigator();
        UUID claim = cleanClaim(actor, "CLM-CLEAN");

        runRules(actor, claim).andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/v1/claims/" + claim + "/rule-runs/")))
                .andExpect(jsonPath("$.claimId").value(claim.toString()))
                .andExpect(jsonPath("$.ruleSetVersion").value("1"))
                .andExpect(jsonPath("$.reusedExistingRun").value(false))
                .andExpect(jsonPath("$.results", hasSize(8)))
                .andExpect(jsonPath("$.results[*].ruleId").value(org.hamcrest.Matchers.contains("REQUIRED_EVIDENCE", "INVOICE_ARITHMETIC", "ESTIMATE_INVOICE_VARIANCE",
                        "DATE_CONSISTENCY", "DOCUMENT_FILE_DUPLICATE", "INVOICE_IDENTITY_DUPLICATE", "REPEATED_LINE_ITEMS", "HISTORICAL_RELATIONSHIP")))
                .andExpect(jsonPath("$.counts.flag").value(0))
                .andExpect(jsonPath("$.counts.error").value(0))
                .andExpect(jsonPath("$.requestedBy").value(actor.userId().toString()))
                .andExpect(jsonPath("$.configuration.roundOffAllowance").value("1.00"))
                .andExpect(jsonPath("$.configurationHash").isString())
                .andExpect(jsonPath("$.inputFingerprint").isString());
        assertThat(runCount(claim)).isEqualTo(1);
    }

    @Test
    void everyResultHasAStableIdThatIsTheStoredRowsId() throws Exception {
        TestActors actor = investigator();
        UUID claim = cleanClaim(actor, "CLM-IDS");

        String run = runOk(actor, claim);
        UUID first = UUID.fromString(JsonPath.read(run, "$.results[0].id"));

        assertThat(JsonPath.<java.util.List<String>>read(run, "$.results[*].id")).doesNotHaveDuplicates().hasSize(8);
        assertThat(jdbc.queryForObject("select rule_id from rule_results where id = ?", String.class, first)).isEqualTo("REQUIRED_EVIDENCE");
        String again = body(latestRun(actor, claim));
        assertThat(JsonPath.<java.util.List<String>>read(again, "$.results[*].id")).isEqualTo(JsonPath.<java.util.List<String>>read(run, "$.results[*].id"));
    }

    @Test
    void theRulesThatCanRunPassAndTheOnesThatLackEvidenceSayWhy() throws Exception {
        TestActors actor = investigator();
        UUID claim = cleanClaim(actor, "CLM-MIXED");

        String run = runOk(actor, claim);

        assertThat(outcome(run, "INVOICE_ARITHMETIC")).isEqualTo("PASS");
        assertThat(outcome(run, "ESTIMATE_INVOICE_VARIANCE")).isEqualTo("PASS");
        assertThat(outcome(run, "DATE_CONSISTENCY")).isEqualTo("PASS");
        assertThat(outcome(run, "DOCUMENT_FILE_DUPLICATE")).isEqualTo("PASS");
        assertThat(outcome(run, "INVOICE_IDENTITY_DUPLICATE")).isEqualTo("PASS");
        assertThat(outcome(run, "REPEATED_LINE_ITEMS")).isEqualTo("PASS");
        assertThat(outcome(run, "HISTORICAL_RELATIONSHIP")).isEqualTo("INCOMPLETE");
        assertThat(outcome(run, "REQUIRED_EVIDENCE")).isEqualTo("INCOMPLETE");      // the seeded checklist also expects a claim form, policy record and more
        assertThat(result(run, "HISTORICAL_RELATIONSHIP").get("explanation").toString()).contains("not the same as finding no match");
    }

    @Test
    void askingAgainWithNothingNewReturnsTheExistingRunInsteadOfCreatingAnother() throws Exception {
        TestActors actor = investigator();
        UUID claim = cleanClaim(actor, "CLM-AGAIN");
        String first = runOk(actor, claim);

        runRules(actor, claim).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(JsonPath.<String>read(first, "$.id")))
                .andExpect(jsonPath("$.reusedExistingRun").value(true));

        assertThat(runCount(claim)).isEqualTo(1);
    }

    @Test
    void newEvidenceOnTheClaimGivesANewRunAndTheOldOneIsKeptUnchanged() throws Exception {
        TestActors actor = investigator();
        UUID claim = cleanClaim(actor, "CLM-GROWS");
        String first = runOk(actor, claim);

        attach(actor, claim, Inputs.plain(DocumentType.CLAIM_FORM).status(DocumentStatus.RECEIVED));
        runRules(actor, claim).andExpect(status().isCreated());

        String latest = body(latestRun(actor, claim));
        assertThat(JsonPath.<String>read(latest, "$.id")).isNotEqualTo(JsonPath.<String>read(first, "$.id"));
        assertThat(runCount(claim)).isEqualTo(2);
        String oldAgain = body(getRun(actor, claim, UUID.fromString(JsonPath.read(first, "$.id"))));
        assertThat(JsonPath.<String>read(oldAgain, "$.inputFingerprint")).isEqualTo(JsonPath.<String>read(first, "$.inputFingerprint"));
        assertThat(JsonPath.<Object>read(oldAgain, "$.results")).isEqualTo(JsonPath.<Object>read(first, "$.results"));
    }

    @Test
    void theSameFileAppearingOnAnotherClaimLaterGivesANewRunWithAFlag() throws Exception {
        TestActors actor = investigator();
        UUID claim = cleanClaim(actor, "CLM-ORIGINAL");
        String sha = Inputs.invoice().sha256;
        UUID invoiceDoc = attachInvoiceWithSha(actor, claim, sha);
        String before = runOk(actor, claim);
        assertThat(outcome(before, "DOCUMENT_FILE_DUPLICATE")).isEqualTo("PASS");

        UUID other = ruleClaim(actor, "CLM-COPY");
        attach(actor, other, Inputs.invoice().sha(sha));
        runRules(actor, claim).andExpect(status().isCreated());

        String after = body(latestRun(actor, claim));
        assertThat(JsonPath.<String>read(after, "$.id")).isNotEqualTo(JsonPath.<String>read(before, "$.id"));
        assertThat(outcome(after, "DOCUMENT_FILE_DUPLICATE")).isEqualTo("FLAG");
        assertThat(result(after, "DOCUMENT_FILE_DUPLICATE").get("severity")).isEqualTo("REVIEW");
        assertThat(result(after, "DOCUMENT_FILE_DUPLICATE").get("explanation").toString()).contains("identical file content");
        assertThat(JsonPath.<java.util.List<Object>>read(after, "$.inputs.sameFileDocumentIds")).hasSize(1);
        assertThat(invoiceDoc).isNotNull();
    }

    private UUID attachInvoiceWithSha(TestActors actor, UUID claim, String sha) {
        // The clean claim already has an invoice with its own checksum; give it the shared one by replacing it.
        jdbc.update("update documents set sha256 = ? where claim_id = ? and document_type = 'FINAL_INVOICE'", sha, claim);
        return jdbc.queryForObject("select id from documents where claim_id = ? and document_type = 'FINAL_INVOICE'", UUID.class, claim);
    }

    @Test
    void aTotalWithAnArithmeticErrorIsFlaggedWithItsEvidence() throws Exception {
        TestActors actor = investigator();
        UUID claim = ruleClaim(actor, "CLM-ARITH");
        attach(actor, claim, Inputs.invoice().set("grandTotal", "17930.00"));
        attach(actor, claim, Inputs.estimate().set("grandTotal", "17930.00"));

        String run = runOk(actor, claim);

        assertThat(outcome(run, "INVOICE_ARITHMETIC")).isEqualTo("FLAG");
        assertThat(result(run, "INVOICE_ARITHMETIC").get("severity")).isEqualTo("REVIEW");
        assertThat(JsonPath.<java.util.List<Object>>read(run, "$.results[?(@.ruleId=='INVOICE_ARITHMETIC')].evidence[0]")).isNotEmpty();
        assertThat(JsonPath.<Integer>read(run, "$.counts.flag")).isGreaterThanOrEqualTo(1);
        assertThat(JsonPath.<java.util.List<Object>>read(run, "$.results[?(@.ruleId=='INVOICE_ARITHMETIC')].considerations[0]")).isNotEmpty();
    }

    @Test
    void aTheftClaimAtFirstNoticeHasNoInvoiceRulesToRun() throws Exception {
        TestActors actor = investigator();
        UUID claim = ruleClaim(actor, "CLM-THEFT", "FNOL", "THEFT", "VEH-9", "GAR-9");

        String run = runOk(actor, claim);

        assertThat(outcome(run, "INVOICE_ARITHMETIC")).isEqualTo("NOT_APPLICABLE");
        assertThat(outcome(run, "ESTIMATE_INVOICE_VARIANCE")).isEqualTo("NOT_APPLICABLE");
        assertThat(outcome(run, "INVOICE_IDENTITY_DUPLICATE")).isEqualTo("NOT_APPLICABLE");
        assertThat(outcome(run, "REPEATED_LINE_ITEMS")).isEqualTo("NOT_APPLICABLE");
        assertThat(JsonPath.<Integer>read(run, "$.counts.notApplicable")).isEqualTo(4);
    }

    @Test
    void anInvoiceThatWasNeverReadMakesTheInvoiceRulesIncompleteNotFlagged() throws Exception {
        TestActors actor = investigator();
        UUID claim = ruleClaim(actor, "CLM-UNREAD");
        attach(actor, claim, Inputs.invoice().notExtracted().status(DocumentStatus.RECEIVED));

        String run = runOk(actor, claim);

        assertThat(outcome(run, "INVOICE_ARITHMETIC")).isEqualTo("INCOMPLETE");
        assertThat(outcome(run, "ESTIMATE_INVOICE_VARIANCE")).isEqualTo("INCOMPLETE");
        assertThat(JsonPath.<Integer>read(run, "$.counts.flag")).isZero();
    }

    @Test
    void anotherOrganizationsClaimAndRunsLookLikeTheyDoNotExist() throws Exception {
        TestActors owner = investigator();
        TestActors stranger = investigator();
        UUID claim = cleanClaim(owner, "CLM-PRIVATE");
        String run = runOk(owner, claim);
        UUID runId = UUID.fromString(JsonPath.read(run, "$.id"));

        runRules(stranger, claim).andExpect(status().isNotFound());
        latestRun(stranger, claim).andExpect(status().isNotFound());
        getRun(stranger, claim, runId).andExpect(status().isNotFound());
        listRuns(stranger, claim).andExpect(status().isNotFound());
        assertThat(runCount(claim)).isEqualTo(1);
    }

    @Test
    void aRunIsOnlyReachableThroughItsOwnClaim() throws Exception {
        TestActors actor = investigator();
        UUID claim = cleanClaim(actor, "CLM-A");
        UUID other = cleanClaim(actor, "CLM-B");
        UUID runId = UUID.fromString(JsonPath.read(runOk(actor, claim), "$.id"));

        getRun(actor, other, runId).andExpect(status().isNotFound());
        getRun(actor, claim, runId).andExpect(status().isOk());
        getRun(actor, claim, UUID.randomUUID()).andExpect(status().isNotFound());
    }

    @Test
    void unknownClaimsAndClaimsNeverRunAreNotFoundAndNoTokenIsRefused() throws Exception {
        TestActors actor = investigator();
        UUID claim = cleanClaim(actor, "CLM-NEVER");

        runRules(actor, UUID.randomUUID()).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        latestRun(actor, claim).andExpect(status().isNotFound());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/claims/" + claim + "/rule-runs")).andExpect(status().isUnauthorized());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/rules")).andExpect(status().isUnauthorized());
    }

    @Test
    void anInvestigatorAndAnAdministratorMayBothRunAndRead() throws Exception {
        TestActors investigator = investigator();
        TestActors admin = adminOf(investigator);
        UUID claim = cleanClaim(investigator, "CLM-ROLES");

        runRules(admin, claim).andExpect(status().isCreated());
        latestRun(investigator, claim).andExpect(status().isOk());
    }

    @Test
    void theListIsNewestFirstPagedAndValidated() throws Exception {
        TestActors actor = investigator();
        UUID claim = cleanClaim(actor, "CLM-LIST");
        String first = runOk(actor, claim);
        attach(actor, claim, Inputs.plain(DocumentType.POLICY_RECORD).status(DocumentStatus.RECEIVED));
        String second = runOk(actor, claim);

        listRuns(actor, claim).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.items[0].id").value(JsonPath.<String>read(second, "$.id")))
                .andExpect(jsonPath("$.items[1].id").value(JsonPath.<String>read(first, "$.id")))
                .andExpect(jsonPath("$.items[0].counts.pass").isNumber());
        listRuns(actor, claim, "size", "1", "page", "1").andExpect(jsonPath("$.items", hasSize(1))).andExpect(jsonPath("$.items[0].id").value(JsonPath.<String>read(first, "$.id")));
        listRuns(actor, claim, "size", "0").andExpect(status().isBadRequest());
        listRuns(actor, claim, "size", "101").andExpect(status().isBadRequest());
        listRuns(actor, claim, "sort", "secret,asc").andExpect(status().isBadRequest());
    }

    @Test
    void theCatalogueListsTheRulesWithTheThresholdsInForce() throws Exception {
        TestActors actor = investigator();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/rules").header("Authorization", actor.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ruleSetVersion").value("1"))
                .andExpect(jsonPath("$.rules", hasSize(8)))
                .andExpect(jsonPath("$.rules[0].ruleId").value("REQUIRED_EVIDENCE"))
                .andExpect(jsonPath("$.rules[2].considerations").isNotEmpty())
                .andExpect(jsonPath("$.configuration.variancePercentThreshold").value("10.00"))
                .andExpect(jsonPath("$.configuration.timeZone").value("Asia/Kolkata"));
    }

    @Test
    void nothingInAResponseUsesFraudLanguage() throws Exception {
        TestActors actor = investigator();
        UUID claim = ruleClaim(actor, "CLM-WORDS");
        attach(actor, claim, Inputs.invoice().set("grandTotal", "17930.00").set("documentDate", "2026-09-01"));
        attach(actor, claim, Inputs.estimate().set("grandTotal", "5000.00"));

        String run = runOk(actor, claim).toLowerCase();

        assertThat(run).doesNotContain("fraud").doesNotContain("suspicious").doesNotContain("guilty");
    }
}
