package com.example.ClaimShieldX.readiness;

import com.example.ClaimShieldX.documents.DocumentApiTestBase;
import com.example.ClaimShieldX.documents.model.DocumentType;
import com.example.ClaimShieldX.support.TestActors;
import com.example.ClaimShieldX.support.TestFiles;
import com.example.ClaimShieldX.support.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The checklist endpoint end to end, against the real migrated checklist table. */
class DocumentRequirementsApiTests extends DocumentApiTestBase {
    private static String url(UUID claim) {
        return "/api/v1/claims/" + claim + "/document-requirements";
    }

    private ResultActions checklist(TestActors actor, UUID claim) throws Exception {
        return mockMvc.perform(get(url(claim)).header("Authorization", actor.bearer()));
    }

    private static String item(DocumentType type, String field) {
        return "$.items[?(@.documentType=='" + type + "')]." + field;
    }

    private UUID row(TestActors actor, UUID claim, DocumentType type, String status, Instant receivedAt, boolean superseded) {
        return fixtures.document(actor.organizationId(), claim, actor.userId(), type, status, receivedAt, superseded);
    }

    // ------------------------------------------------------------ access

    @Test
    void anonymousAndBadTokensAreRejected() throws Exception {
        TestActors actor = actor();
        UUID claim = claimFor(actor);

        mockMvc.perform(get(url(claim))).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(get(url(claim)).header("Authorization", "Bearer " + TestTokens.expired(jwtSecret, actor.email())))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(url(claim)).header("Authorization", "Bearer " + TestTokens.signedWithAnotherKey(actor.email())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aClaimOfAnotherOrganizationLooksExactlyLikeAClaimThatDoesNotExist() throws Exception {
        TestActors owner = actor();
        TestActors stranger = actor();
        UUID claim = claimFor(owner);

        String foreign = checklist(stranger, claim).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andReturn().getResponse().getContentAsString();
        String missing = checklist(stranger, UUID.randomUUID()).andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();

        assertThat(foreign.replaceAll("\"timestamp\":\"[^\"]*\"", "")).isEqualTo(missing.replaceAll("\"timestamp\":\"[^\"]*\"", ""));
        assertThat(foreign).doesNotContain(owner.organizationId().toString());
    }

    @Test
    void aMalformedClaimIdIsAValidationError() throws Exception {
        TestActors actor = actor();

        mockMvc.perform(get("/api/v1/claims/not-a-uuid/document-requirements").header("Authorization", actor.bearer()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void bothRolesCanReadTheChecklist() throws Exception {
        for (String role : new String[]{"INVESTIGATOR", "ORG_ADMIN"}) {
            TestActors actor = actor(role);
            checklist(actor, claimFor(actor)).andExpect(status().isOk());
        }
    }

    // ------------------------------------------------------------ content

    @Test
    void anEmptyClaimAtTheFinalStageIsNotReadyAndSaysWhichChecksCannotRun() throws Exception {
        TestActors actor = actor();
        UUID claim = claimFor(actor);

        checklist(actor, claim)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimId").value(claim.toString()))
                .andExpect(jsonPath("$.stage").value("FINAL_INVOICE_RECEIVED"))
                .andExpect(jsonPath("$.incidentType").value("COLLISION"))
                .andExpect(jsonPath("$.readiness").value("NOT_READY"))
                .andExpect(jsonPath("$.items", hasSize(13)))
                .andExpect(jsonPath("$.blockers", hasSize(1)))
                .andExpect(jsonPath("$.blockers[0].code").value("DOCUMENT_MISSING"))
                .andExpect(jsonPath("$.blockers[0].documentType").value("FINAL_INVOICE"))
                .andExpect(jsonPath("$.blockers[0].requirement").value("REQUIRED"))
                .andExpect(jsonPath("$.blockers[0].blocks", hasItem("ESTIMATE_INVOICE_VARIANCE")))
                .andExpect(jsonPath(item(DocumentType.FINAL_INVOICE, "requirement")).value(contains("REQUIRED")))
                .andExpect(jsonPath(item(DocumentType.FINAL_INVOICE, "evidenceState")).value(contains("NOT_RECEIVED")))
                .andExpect(jsonPath(item(DocumentType.FINAL_INVOICE, "documentIds")).value(contains(empty())))
                .andExpect(jsonPath(item(DocumentType.GARAGE_ESTIMATE, "blocks")).value(contains(empty())))
                .andExpect(jsonPath("$.warnings[*].documentType", hasItems("CLAIM_FORM", "POLICY_RECORD", "GARAGE_ESTIMATE")));
    }

    @Test
    void exactlyOneDocumentIsMandatoryAtEachStageAndNoneAfterSettlement() throws Exception {
        TestActors actor = actor();
        String[][] cases = {
                {"FNOL", "COLLISION", "CLAIM_FORM"},
                {"ESTIMATE_RECEIVED", "COLLISION", "GARAGE_ESTIMATE"},
                {"FINAL_INVOICE_RECEIVED", "COLLISION", "FINAL_INVOICE"},
                {"FINAL_INVOICE_RECEIVED", "THEFT", "FINAL_INVOICE"},
                {"FINAL_INVOICE_RECEIVED", "OTHER", "FINAL_INVOICE"}};
        for (String[] c : cases) {
            UUID claim = fixtures.claim(actor.organizationId(), "M-" + UUID.randomUUID(), c[0], c[1]);

            checklist(actor, claim).andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[?(@.mandatory==true)].documentType", contains(c[2])))
                    .andExpect(jsonPath("$.blockers[*].documentType", contains(c[2])));
        }
        UUID settled = fixtures.claim(actor.organizationId(), "M-" + UUID.randomUUID(), "POST_SETTLEMENT", "COLLISION");
        checklist(actor, settled).andExpect(jsonPath("$.items", hasSize(0))).andExpect(jsonPath("$.blockers", hasSize(0)));
    }

    @Test
    void theUserCanProceedWithoutEveryDocumentExceptTheMandatoryOne() throws Exception {
        TestActors actor = actor();
        UUID claim = claimFor(actor);

        // Nothing at all: cannot proceed, and only the mandatory document is the reason.
        checklist(actor, claim).andExpect(jsonPath("$.readiness").value("NOT_READY"))
                .andExpect(jsonPath("$.blockers", hasSize(1)))
                .andExpect(jsonPath(item(DocumentType.FINAL_INVOICE, "mandatory")).value(contains(true)))
                .andExpect(jsonPath(item(DocumentType.GARAGE_ESTIMATE, "mandatory")).value(contains(false)))
                .andExpect(jsonPath(item(DocumentType.CLAIM_FORM, "mandatory")).value(contains(false)));

        // The mandatory document alone is enough to proceed, even though a required claim form, policy and estimate are missing.
        row(actor, claim, DocumentType.FINAL_INVOICE, "RECEIVED", Instant.now(), false);
        checklist(actor, claim).andExpect(jsonPath("$.readiness").value("READY_WITH_WARNINGS"))
                .andExpect(jsonPath("$.blockers", hasSize(0)))
                .andExpect(jsonPath("$.warnings[*].documentType", hasItems("CLAIM_FORM", "POLICY_RECORD", "GARAGE_ESTIMATE")));

        // If the mandatory document could not be processed, the user cannot proceed again.
        jdbc.update("update documents set status = 'FAILED' where claim_id = ?", claim);
        checklist(actor, claim).andExpect(jsonPath("$.readiness").value("NOT_READY"));
    }

    @Test
    void aRealUploadMakesTheInvoiceReceivedButNotVerified() throws Exception {
        TestActors actor = actor();
        UUID claim = claimFor(actor);
        UUID invoice = uploadOk(actor, claim, pdf(TestFiles.pdf(1)), "FINAL_INVOICE");

        checklist(actor, claim)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readiness").value("READY_WITH_WARNINGS"))
                .andExpect(jsonPath("$.blockers", hasSize(0)))
                .andExpect(jsonPath(item(DocumentType.FINAL_INVOICE, "evidenceState")).value(contains("RECEIVED")))
                .andExpect(jsonPath(item(DocumentType.FINAL_INVOICE, "documentIds")).value(contains(contains(invoice.toString()))));
    }

    @Test
    void theStateFollowsTheDocumentStatus() throws Exception {
        TestActors actor = actor();
        UUID claim = claimFor(actor);
        UUID invoice = row(actor, claim, DocumentType.FINAL_INVOICE, "RECEIVED", Instant.now(), false);

        for (String status : new String[]{"PROCESSING", "VERIFIED", "NEEDS_REVIEW", "FAILED"}) {
            jdbc.update("update documents set status = ? where id = ?", status, invoice);

            ResultActions result = checklist(actor, claim).andExpect(status().isOk())
                    .andExpect(jsonPath(item(DocumentType.FINAL_INVOICE, "evidenceState")).value(contains(status)));
            if (status.equals("FAILED")) {
                result.andExpect(jsonPath("$.readiness").value("NOT_READY")).andExpect(jsonPath("$.blockers[0].code").value("DOCUMENT_FAILED"));
            } else {
                result.andExpect(jsonPath("$.blockers", hasSize(0)));
            }
        }
    }

    @Test
    void aSupersededDocumentIsNotCurrentEvidence() throws Exception {
        TestActors actor = actor();
        UUID claim = claimFor(actor);
        UUID old = row(actor, claim, DocumentType.FINAL_INVOICE, "VERIFIED", Instant.now().minusSeconds(600), true);
        UUID current = row(actor, claim, DocumentType.FINAL_INVOICE, "NEEDS_REVIEW", Instant.now().minusSeconds(60), false);

        checklist(actor, claim).andExpect(status().isOk())
                .andExpect(jsonPath(item(DocumentType.FINAL_INVOICE, "evidenceState")).value(contains("NEEDS_REVIEW")))
                .andExpect(jsonPath(item(DocumentType.FINAL_INVOICE, "documentIds")).value(contains(contains(current.toString()))));
        assertThat(old).isNotEqualTo(current);
    }

    @Test
    void whenSeveralCurrentDocumentsExistTheNewestDecidesTheStateAndAllAreListed() throws Exception {
        TestActors actor = actor();
        UUID claim = claimFor(actor);
        UUID first = row(actor, claim, DocumentType.DAMAGE_PHOTOGRAPH, "FAILED", Instant.now().minusSeconds(120), false);
        UUID second = row(actor, claim, DocumentType.DAMAGE_PHOTOGRAPH, "VERIFIED", Instant.now().minusSeconds(60), false);

        checklist(actor, claim).andExpect(status().isOk())
                .andExpect(jsonPath(item(DocumentType.DAMAGE_PHOTOGRAPH, "evidenceState")).value(contains("VERIFIED")))
                .andExpect(jsonPath(item(DocumentType.DAMAGE_PHOTOGRAPH, "documentIds")).value(contains(contains(second.toString(), first.toString()))));
    }

    @Test
    void aTheftClaimNeedsTheFirAndTheftEvidence() throws Exception {
        TestActors actor = actor();
        UUID theft = fixtures.claim(actor.organizationId(), "THEFT-" + UUID.randomUUID(), "FINAL_INVOICE_RECEIVED", "THEFT");
        UUID collision = claimFor(actor);

        checklist(actor, theft).andExpect(status().isOk()).andExpect(jsonPath("$.incidentType").value("THEFT"))
                .andExpect(jsonPath(item(DocumentType.FIR_POLICE_REPORT, "requirement")).value(contains("REQUIRED")))
                .andExpect(jsonPath(item(DocumentType.THEFT_EVIDENCE, "requirement")).value(contains("REQUIRED")))
                .andExpect(jsonPath("$.warnings[*].documentType", hasItems("FIR_POLICE_REPORT", "THEFT_EVIDENCE")));
        checklist(actor, collision).andExpect(status().isOk())
                .andExpect(jsonPath(item(DocumentType.FIR_POLICE_REPORT, "requirement")).value(contains("OPTIONAL")))
                .andExpect(jsonPath(item(DocumentType.THEFT_EVIDENCE, "requirement")).value(contains("NOT_APPLICABLE")))
                .andExpect(jsonPath("$.warnings[*].documentType", not(hasItem("THEFT_EVIDENCE"))));
    }

    @Test
    void theFinalInvoiceIsNotAskedForAtFirstNotice() throws Exception {
        TestActors actor = actor();
        UUID claim = fixtures.claim(actor.organizationId(), "FNOL-" + UUID.randomUUID(), "FNOL", "COLLISION");

        checklist(actor, claim).andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("FNOL"))
                .andExpect(jsonPath("$.readiness").value("NOT_READY"))
                .andExpect(jsonPath("$.blockers[0].documentType").value("CLAIM_FORM"))
                .andExpect(jsonPath(item(DocumentType.FINAL_INVOICE, "requirement")).value(contains("NOT_APPLICABLE")))
                .andExpect(jsonPath("$.blockers[*].documentType", not(hasItem("FINAL_INVOICE"))))
                .andExpect(jsonPath("$.warnings[*].documentType", not(hasItem("FINAL_INVOICE"))));

        row(actor, claim, DocumentType.CLAIM_FORM, "VERIFIED", Instant.now(), false);
        row(actor, claim, DocumentType.POLICY_RECORD, "VERIFIED", Instant.now(), false);
        checklist(actor, claim).andExpect(jsonPath("$.readiness").value("READY"))
                .andExpect(jsonPath("$.blockers", hasSize(0))).andExpect(jsonPath("$.warnings", hasSize(0)));
    }

    @Test
    void aSettledClaimHasNoChecklistAndSaysSo() throws Exception {
        TestActors actor = actor();
        UUID claim = fixtures.claim(actor.organizationId(), "POST-" + UUID.randomUUID(), "POST_SETTLEMENT", "COLLISION");

        checklist(actor, claim).andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.readiness").value("READY_WITH_WARNINGS"))
                .andExpect(jsonPath("$.warnings", hasSize(1)))
                .andExpect(jsonPath("$.warnings[0].code").value("NO_CHECKLIST_FOR_STAGE"))
                .andExpect(jsonPath("$.warnings[0].documentType").doesNotExist());
    }

    // ------------------------------------------------------------ safety

    @Test
    void theResponseNeverRevealsStorageDetailsOrDocumentContents() throws Exception {
        TestActors actor = actor();
        UUID claim = claimFor(actor);
        uploadOk(actor, claim, pdf(TestFiles.pdf(7)), "FINAL_INVOICE");
        String key = jdbc.queryForObject("select object_key from documents where claim_id = ?", String.class, claim);
        String sha = jdbc.queryForObject("select sha256 from documents where claim_id = ?", String.class, claim);

        String body = checklist(actor, claim).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(key).doesNotContain(sha).doesNotContain("objectKey").doesNotContain("sha256")
                .doesNotContain("fileName").doesNotContain(actor.organizationId().toString()).doesNotContain("invoice.pdf");
    }

    @Test
    void readingTheChecklistChangesNothing() throws Exception {
        TestActors actor = actor();
        UUID claim = claimFor(actor);
        row(actor, claim, DocumentType.FINAL_INVOICE, "RECEIVED", Instant.now(), false);
        int documentsBefore = documentCount(claim);
        String statusBefore = jdbc.queryForObject("select status from documents where claim_id = ?", String.class, claim);

        String first = checklist(actor, claim).andReturn().getResponse().getContentAsString();
        String second = checklist(actor, claim).andReturn().getResponse().getContentAsString();

        assertThat(second).isEqualTo(first);
        assertThat(documentCount(claim)).isEqualTo(documentsBefore);
        assertThat(jdbc.queryForObject("select status from documents where claim_id = ?", String.class, claim)).isEqualTo(statusBefore);
    }

    @Test
    void noWordingEverCallsAClaimFraudulent() throws Exception {
        TestActors actor = actor();
        String body = checklist(actor, claimFor(actor)).andReturn().getResponse().getContentAsString().toLowerCase();

        assertThat(body).doesNotContain("fraud").doesNotContain("suspicious").doesNotContain("reject");
    }
}
