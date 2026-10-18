package com.example.ClaimShieldX.investigation;

import com.example.ClaimShieldX.extraction.client.ModelCallException;
import com.example.ClaimShieldX.extraction.client.ModelCallException.Category;
import com.example.ClaimShieldX.extraction.support.TestPdfs;
import com.example.ClaimShieldX.support.TestActors;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static com.example.ClaimShieldX.extraction.support.TestDocuments.invoice;
import static com.example.ClaimShieldX.extraction.support.TestDocuments.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The real stage handlers end to end: real database, storage, extraction service, rules and readiness; only the model is scripted. */
class InvestigationPipelineTests extends InvestigationApiTestBase {
    private final String validInvoice = json(invoice(GARAGE, VEHICLE, "12/09/2026"));

    private UUID invoiceFor(TestActors actor, UUID claim) throws Exception {
        return uploadOk(actor, claim, pdf(TestPdfs.pdf(1, ++seed)), "FINAL_INVOICE");
    }

    private UUID estimateFor(TestActors actor, UUID claim) throws Exception {
        return uploadOk(actor, claim, pdf(TestPdfs.pdf(1, ++seed)), "GARAGE_ESTIMATE");
    }

    private ModelCallException failure(Category category) {
        return new ModelCallException(category, category.name(), null);
    }

    /** A transient failure that survives the model caller's own retries and fallback model (3 calls on each of 2 models). */
    private void scriptModelDown() {
        for (int i = 0; i < 6; i++) {
            model.thenFail(failure(Category.TRANSIENT));
        }
    }

    // ---------------------------------------------------------------- the whole path

    @Test
    void aClaimWithAnInvoiceIsPreparedReadRuledAndCompleted() throws Exception {
        TestActors actor = actor();
        UUID claim = claimWithKnownFacts(actor);
        UUID document = invoiceFor(actor, claim);
        model.thenReturn(validInvoice);
        UUID id = createOk(actor, claim);

        assertThat(drain()).isEqualTo(1);

        String body = investigationBody(actor, id);
        assertThat(JsonPath.<String>read(body, "$.status")).isEqualTo("COMPLETED");
        assertThat(JsonPath.<String>read(body, "$.finishedAt")).isNotNull();
        assertThat(JsonPath.<java.util.List<String>>read(body, "$.stages[*].status")).containsExactly("OK", "OK", "OK", "SKIPPED", "OK", "OK");
        assertThat(JsonPath.<String>read(body, "$.stages[3].summary.reason")).isEqualTo("NO_CONCERNS_TO_ANALYZE");
        assertThat(JsonPath.<String>read(body, "$.snapshotId")).isEqualTo(JsonPath.<String>read(body, "$.stages[2].summary.snapshotId"));
        assertThat(JsonPath.<String>read(body, "$.reportId")).isNotNull();
        assertThat(JsonPath.<String>read(body, "$.reportStatus")).isEqualTo("CURRENT");
        assertThat(documentStatus(document)).isEqualTo("VERIFIED");
        assertThat(model.callCount()).isEqualTo(1);

        String ruleRunId = JsonPath.read(body, "$.ruleRunId");
        assertThat(ruleRunId).isNotNull();
        assertThat(JsonPath.<String>read(body, "$.stages[2].summary.ruleRunId")).isEqualTo(ruleRunId);
        mockMvc.perform(get("/api/v1/claims/" + claim + "/rule-runs/" + ruleRunId).header("Authorization", actor.bearer())).andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(8)));
        assertThat(JsonPath.<String>read(body, "$.stages[1].summary.documents[0].documentId")).isEqualTo(document.toString());
        assertThat(JsonPath.<String>read(body, "$.stages[1].summary.documents[0].outcome")).isEqualTo("READ");
    }

    @Test
    void theStatusResponseNeverContainsAnythingFromTheDocumentsOrTheModel() throws Exception {
        TestActors actor = actor();
        UUID claim = claimWithKnownFacts(actor);
        invoiceFor(actor, claim);
        model.thenReturn(validInvoice);
        UUID id = createOk(actor, claim);
        drain();

        String body = investigationBody(actor, id);

        assertThat(body).doesNotContain(GARAGE).doesNotContain(VEHICLE).doesNotContain("12/09/2026").doesNotContain("Grand total").doesNotContain("quote");
    }

    @Test
    void anInvestigationWithoutTheGatingDocumentWaitsAndStartsAsSoonAsItIsUploaded() throws Exception {
        TestActors actor = actor();
        UUID claim = claimWithKnownFacts(actor);
        UUID id = createOk(actor, claim);

        drain();

        getInvestigation(actor, id).andExpect(jsonPath("$.status").value("WAITING_FOR_EVIDENCE"))
                .andExpect(jsonPath("$.waitingFor[0].documentType").value("FINAL_INVOICE"))
                .andExpect(jsonPath("$.waitingFor[0].code").value("DOCUMENT_MISSING"))
                .andExpect(jsonPath("$.stages[0].status").value("PENDING")).andExpect(jsonPath("$.attempt").value(1));
        assertThat(drain()).as("nothing to do until the document arrives or the re-check is due").isZero();

        invoiceFor(actor, claim);          // the upload wakes the waiting investigation
        model.thenReturn(validInvoice);
        assertThat(drain()).isEqualTo(1);

        getInvestigation(actor, id).andExpect(jsonPath("$.status").value("COMPLETED")).andExpect(jsonPath("$.waitingFor").doesNotExist());
    }

    @Test
    void anInvoiceTheGatingDocumentIsEnoughOtherMissingDocumentsOnlyMakeSomeRulesIncomplete() throws Exception {
        TestActors actor = actor();
        UUID claim = claimWithKnownFacts(actor);
        invoiceFor(actor, claim);
        model.thenReturn(validInvoice);
        UUID id = createOk(actor, claim);
        drain();

        String ruleRun = JsonPath.read(investigationBody(actor, id), "$.ruleRunId");
        String run = mockMvc.perform(get("/api/v1/claims/" + claim + "/rule-runs/" + ruleRun).header("Authorization", actor.bearer())).andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<List<String>>read(run, "$.results[?(@.ruleId=='REQUIRED_EVIDENCE')].outcome")).containsExactly("INCOMPLETE");
        assertThat(JsonPath.<List<String>>read(run, "$.results[?(@.ruleId=='INVOICE_ARITHMETIC')].outcome")).containsExactly("PASS");
    }

    // ---------------------------------------------------------------- failures in the middle

    @Test
    void whenTheModelIsDownTheInvestigationSleepsRetriesAndFinishesReadingOnlyWhatIsStillUnread() throws Exception {
        TestActors actor = actor();
        UUID claim = claimWithKnownFacts(actor);
        UUID invoice = invoiceFor(actor, claim);
        UUID estimate = estimateFor(actor, claim);
        model.thenReturn(validInvoice);      // the first document (oldest) is read
        scriptModelDown();                   // the second cannot be read
        UUID id = createOk(actor, claim);

        drain();

        String sleeping = investigationBody(actor, id);
        assertThat(JsonPath.<String>read(sleeping, "$.status")).isEqualTo("RETRY_WAIT");
        assertThat(JsonPath.<Integer>read(sleeping, "$.attempt")).isEqualTo(2);
        assertThat(JsonPath.<String>read(sleeping, "$.nextRetryAt")).isNotNull();
        assertThat(JsonPath.<String>read(sleeping, "$.error.code")).isEqualTo("MODEL_UNAVAILABLE");
        assertThat(JsonPath.<String>read(sleeping, "$.error.message")).isEqualTo("The AI service was unavailable.");
        assertThat(JsonPath.<String>read(sleeping, "$.stages[0].status")).isEqualTo("OK");
        assertThat(JsonPath.<String>read(sleeping, "$.stages[1].status")).isEqualTo("PENDING");
        assertThat(documentStatus(invoice)).isEqualTo("VERIFIED");
        assertThat(documentStatus(estimate)).isEqualTo("FAILED");
        assertThat(model.callCount()).isEqualTo(7);
        assertThat(drain()).as("not due yet").isZero();

        model.thenReturn(validInvoice);
        makeDue(id);
        assertThat(drain()).isEqualTo(1);

        assertThat(investigationBody(actor, id)).contains("\"status\":\"COMPLETED\"");
        assertThat(documentStatus(estimate)).isEqualTo("VERIFIED");
        assertThat(model.callCount()).as("the document already read was not read again").isEqualTo(8);
        String done = investigationBody(actor, id);
        assertThat(JsonPath.<String>read(done, "$.error")).isNull();
        assertThat(JsonPath.<String>read(done, "$.stages[1].status")).as("everything is read now, so the stage is OK").isEqualTo("OK");
        assertThat(JsonPath.<List<String>>read(done, "$.stages[1].summary.documents[?(@.documentId=='" + invoice + "')].outcome")).containsExactly("ALREADY_READ");
        assertThat(JsonPath.<List<String>>read(done, "$.stages[1].summary.documents[?(@.documentId=='" + estimate + "')].outcome")).containsExactly("READ");
        assertThat(JsonPath.<Integer>read(done, "$.stages[1].summary.alreadyRead")).isEqualTo(1);
        assertThat(JsonPath.<Integer>read(done, "$.stages[1].summary.failed")).isZero();
    }

    @Test
    void whenTheModelStaysDownTheReadingStageIsDegradedAndTheRulesStillRun() throws Exception {
        TestActors actor = actor();
        UUID claim = claimWithKnownFacts(actor);
        UUID document = invoiceFor(actor, claim);
        for (int attempt = 0; attempt < 3; attempt++) {
            scriptModelDown();
        }
        UUID id = createOk(actor, claim);

        drain();
        makeDue(id);
        drain();
        makeDue(id);
        drain();

        String body = investigationBody(actor, id);
        assertThat(JsonPath.<String>read(body, "$.status")).isEqualTo("COMPLETED");
        assertThat(JsonPath.<java.util.List<String>>read(body, "$.stages[*].status")).containsExactly("OK", "DEGRADED", "OK", "SKIPPED", "OK", "OK");
        assertThat(JsonPath.<String>read(body, "$.stages[1].errorCode")).isEqualTo("MODEL_UNAVAILABLE");
        assertThat(JsonPath.<Boolean>read(body, "$.stages[1].summary.retriesExhausted")).isTrue();
        assertThat(documentStatus(document)).isEqualTo("FAILED");
        assertThat(model.callCount()).isEqualTo(18);
        String ruleRun = JsonPath.read(body, "$.ruleRunId");
        String run = mockMvc.perform(get("/api/v1/claims/" + claim + "/rule-runs/" + ruleRun).header("Authorization", actor.bearer())).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(run, "$.results[?(@.ruleId=='INVOICE_ARITHMETIC')].outcome")).containsExactly("INCOMPLETE");

        String report = mockMvc.perform(get("/api/v1/investigations/" + id + "/report").header("Authorization", actor.bearer())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(report, "$.completeness")).isEqualTo("PARTIAL");
        assertThat(JsonPath.<List<String>>read(report, "$.unavailableSections")).containsExactly("DOCUMENT_READING");
    }

    @Test
    void aDocumentTheModelRefusesIsRecordedAndDegradesTheStageWithoutRetrying() throws Exception {
        TestActors actor = actor();
        UUID claim = claimWithKnownFacts(actor);
        UUID document = invoiceFor(actor, claim);
        model.thenFail(failure(Category.REJECTED_INPUT));
        UUID id = createOk(actor, claim);

        drain();

        String body = investigationBody(actor, id);
        assertThat(JsonPath.<String>read(body, "$.status")).isEqualTo("COMPLETED");
        assertThat(JsonPath.<String>read(body, "$.stages[1].status")).isEqualTo("DEGRADED");
        assertThat(JsonPath.<String>read(body, "$.stages[1].summary.documents[0].outcome")).isEqualTo("FAILED_FOR_GOOD");
        assertThat(JsonPath.<String>read(body, "$.stages[1].summary.documents[0].code")).isEqualTo("EXTRACTION_FAILED");
        assertThat(JsonPath.<Integer>read(body, "$.attempt")).isEqualTo(1);
        assertThat(model.callCount()).isEqualTo(1);
        assertThat(documentStatus(document)).isEqualTo("FAILED");
    }

    @Test
    void aDocumentThatCanNeverBeReadIsNotSentToTheModelAgainWhenAnotherOneIsRetried() throws Exception {
        TestActors actor = actor();
        UUID claim = claimWithKnownFacts(actor);
        UUID refused = invoiceFor(actor, claim);
        UUID flaky = estimateFor(actor, claim);
        model.thenFail(failure(Category.REJECTED_INPUT));        // the first document is refused for good
        scriptModelDown();                                       // the second cannot be read this time
        UUID id = createOk(actor, claim);

        drain();
        assertThat(investigationStatus(id)).isEqualTo("RETRY_WAIT");
        assertThat(model.callCount()).isEqualTo(7);

        model.thenReturn(validInvoice);
        makeDue(id);
        drain();

        assertThat(investigationStatus(id)).isEqualTo("COMPLETED");
        assertThat(model.callCount()).as("only the second document was sent again").isEqualTo(8);
        assertThat(documentStatus(refused)).isEqualTo("FAILED");
        assertThat(documentStatus(flaky)).isEqualTo("VERIFIED");
        assertThat(JsonPath.<String>read(investigationBody(actor, id), "$.stages[1].status")).isEqualTo("DEGRADED");
    }

    @Test
    void aDocumentAWorkerDiedWhileReadingIsWaitedForWithoutUsingAnAttemptAndThenRead() throws Exception {
        TestActors actor = actor();
        UUID claim = claimWithKnownFacts(actor);
        UUID invoice = invoiceFor(actor, claim);
        UUID abandoned = estimateFor(actor, claim);
        // The process died while it was reading the estimate: the document is still marked as being read, a moment ago.
        jdbc.update("update documents set status = 'PROCESSING', updated_at = now() where id = ?", abandoned);
        model.thenReturn(validInvoice);
        UUID id = createOk(actor, claim);

        drain();

        String sleeping = investigationBody(actor, id);
        assertThat(JsonPath.<String>read(sleeping, "$.status")).isEqualTo("RETRY_WAIT");
        assertThat(JsonPath.<Integer>read(sleeping, "$.attempt")).as("waiting for a document held elsewhere uses no attempt").isEqualTo(1);
        assertThat(JsonPath.<Object>read(sleeping, "$.error")).isNull();
        assertThat(JsonPath.<List<String>>read(sleeping, "$.stages[1].summary.documents[?(@.documentId=='" + abandoned + "')].outcome")).containsExactly("BUSY");
        assertThat(JsonPath.<Integer>read(sleeping, "$.stages[1].summary.busy")).isEqualTo(1);
        assertThat(documentStatus(invoice)).as("the other document was read meanwhile").isEqualTo("VERIFIED");
        assertThat(model.callCount()).isEqualTo(1);
        assertThat(documentStatus(abandoned)).isEqualTo("PROCESSING");
        // The wait is short (re-checked every 15 s), not the whole processing timeout.
        java.sql.Timestamp next = jdbc.queryForObject("select next_attempt_at from investigations where id = ?", java.sql.Timestamp.class, id);
        assertThat(next.toInstant()).isBefore(java.time.Instant.now().plusSeconds(20));

        // Five minutes pass: the abandoned document can be taken again, and the investigation reads it.
        jdbc.update("update documents set updated_at = now() - interval '6 minutes' where id = ?", abandoned);
        model.thenReturn(validInvoice);
        makeDue(id);
        drain();

        String done = investigationBody(actor, id);
        assertThat(JsonPath.<String>read(done, "$.status")).isEqualTo("COMPLETED");
        assertThat(JsonPath.<Integer>read(done, "$.attempt")).isEqualTo(1);
        assertThat(JsonPath.<String>read(done, "$.stages[1].status")).isEqualTo("OK");
        assertThat(documentStatus(abandoned)).isEqualTo("VERIFIED");
        assertThat(model.callCount()).isEqualTo(2);
    }

    @Test
    void aDocumentAbandonedLongAgoIsTakenAndReadAtOnce() throws Exception {
        TestActors actor = actor();
        UUID claim = claimWithKnownFacts(actor);
        UUID abandoned = invoiceFor(actor, claim);
        jdbc.update("update documents set status = 'PROCESSING', updated_at = now() - interval '10 minutes' where id = ?", abandoned);
        model.thenReturn(validInvoice);
        UUID id = createOk(actor, claim);

        drain();

        assertThat(investigationStatus(id)).isEqualTo("COMPLETED");
        assertThat(documentStatus(abandoned)).isEqualTo("VERIFIED");
        assertThat(model.callCount()).isEqualTo(1);
    }

    @Test
    void whenOneDocumentIsBusyAndAnotherFailsTransientlyTheFailureCountsAsAnAttemptAndTheBusyOneIsLookedAtAgain() throws Exception {
        TestActors actor = actor();
        UUID claim = claimWithKnownFacts(actor);
        UUID busy = invoiceFor(actor, claim);
        UUID flaky = estimateFor(actor, claim);
        jdbc.update("update documents set status = 'PROCESSING', updated_at = now() where id = ?", busy);
        scriptModelDown();
        UUID id = createOk(actor, claim);

        drain();

        String body = investigationBody(actor, id);
        assertThat(JsonPath.<String>read(body, "$.status")).isEqualTo("RETRY_WAIT");
        assertThat(JsonPath.<Integer>read(body, "$.attempt")).isEqualTo(2);
        assertThat(JsonPath.<String>read(body, "$.error.code")).isEqualTo("MODEL_UNAVAILABLE");
        assertThat(documentStatus(flaky)).isEqualTo("FAILED");
    }

    @Test
    void aDocumentThatNeedsReviewDegradesTheStageButIsNotReadAgain() throws Exception {
        TestActors actor = actor();
        UUID claim = claimWithKnownFacts(actor);
        UUID document = invoiceFor(actor, claim);
        model.thenReturn(json(invoice("SOME-OTHER-GARAGE", VEHICLE, "12/09/2026")));
        UUID id = createOk(actor, claim);

        drain();

        String body = investigationBody(actor, id);
        assertThat(JsonPath.<String>read(body, "$.status")).isEqualTo("COMPLETED");
        assertThat(JsonPath.<String>read(body, "$.stages[1].status")).isEqualTo("DEGRADED");
        assertThat(JsonPath.<Integer>read(body, "$.stages[1].summary.needsReview")).isEqualTo(1);
        assertThat(documentStatus(document)).isEqualTo("NEEDS_REVIEW");
        assertThat(model.callCount()).isEqualTo(1);
    }

    @Test
    void aStoredDocumentThatCannotBeReadIsAStorageProblemThatIsRetried() throws Exception {
        TestActors actor = actor();
        UUID claim = claimWithKnownFacts(actor);
        UUID document = invoiceFor(actor, claim);
        storage.delete(objectKeyOf(document));
        UUID id = createOk(actor, claim);

        drain();

        String body = investigationBody(actor, id);
        assertThat(JsonPath.<String>read(body, "$.status")).isEqualTo("RETRY_WAIT");
        assertThat(JsonPath.<String>read(body, "$.error.code")).isEqualTo("STORAGE_UNAVAILABLE");
        assertThat(JsonPath.<String>read(body, "$.error.message")).isEqualTo("A stored document could not be read.");
        assertThat(model.callCount()).as("an unreadable original is never sent to the model").isZero();
    }

    @Test
    void aRateLimitedModelIsReportedAsAQuotaProblem() throws Exception {
        TestActors actor = actor();
        UUID claim = claimWithKnownFacts(actor);
        invoiceFor(actor, claim);
        for (int i = 0; i < 6; i++) {
            model.thenFail(failure(Category.QUOTA_EXHAUSTED));
        }
        UUID id = createOk(actor, claim);

        drain();

        getInvestigation(actor, id).andExpect(jsonPath("$.status").value("RETRY_WAIT")).andExpect(jsonPath("$.error.code").value("MODEL_QUOTA_EXHAUSTED"))
                .andExpect(jsonPath("$.stages[1].summary.willRetry").value(1));
    }

    @Test
    void aCancelWhileSleepingBeforeARetryStopsAllFurtherModelCalls() throws Exception {
        TestActors actor = actor();
        UUID claim = claimWithKnownFacts(actor);
        invoiceFor(actor, claim);
        scriptModelDown();
        UUID id = createOk(actor, claim);
        drain();
        assertThat(investigationStatus(id)).isEqualTo("RETRY_WAIT");

        postAction(actor, id, "cancel", newKey()).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        makeDue(id);

        assertThat(drain()).isZero();
        assertThat(model.callCount()).isEqualTo(6);
    }

    @Test
    void anInvestigationSurvivesAnApplicationRestartBecauseItsStateIsInTheDatabase() throws Exception {
        TestActors actor = actor();
        UUID claim = claimWithKnownFacts(actor);
        invoiceFor(actor, claim);
        UUID id = createOk(actor, claim);
        // "Restart": nothing in memory holds the job. A runner that has never seen it picks it up from the database alone.
        model.thenReturn(validInvoice);

        boolean picked = runner.runNext("worker-after-restart");

        assertThat(picked).isTrue();
        assertThat(investigationStatus(id)).isEqualTo("COMPLETED");
    }

    @Test
    void theFailedInvestigationCanBeRetriedFromTheApiAndResumesAtTheFailedStage() throws Exception {
        TestActors actor = actor();
        UUID claim = claimWithKnownFacts(actor);
        invoiceFor(actor, claim);
        model.thenReturn(validInvoice);
        UUID id = createOk(actor, claim);
        drain();
        assertThat(investigationStatus(id)).isEqualTo("COMPLETED");
        // Make the rules stage "fail" afterwards, as if a later run had gone wrong: FAILED with its stage failed and the earlier stages done.
        jdbc.update("update investigations set status = 'FAILED', error_code = 'INTERNAL_ERROR', error_message = 'x' where id = ?", id);
        jdbc.update("update investigation_stages set status = 'FAILED' where investigation_id = ? and stage = 'RUNNING_RULES'", id);

        postAction(actor, id, "retry", newKey()).andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("QUEUED"));
        drain();

        assertThat(investigationStatus(id)).isEqualTo("COMPLETED");
        assertThat(model.callCount()).as("the documents were not read again").isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from rule_runs where claim_id = ?", Integer.class, claim)).as("the stored rule run was reused").isEqualTo(1);
    }
}
