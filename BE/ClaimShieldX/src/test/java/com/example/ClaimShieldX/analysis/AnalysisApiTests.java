package com.example.ClaimShieldX.analysis;

import com.example.ClaimShieldX.analysis.snapshot.SnapshotService;
import com.example.ClaimShieldX.analysis.support.FakeReasoningClient;
import com.example.ClaimShieldX.extraction.client.ModelCallException;
import com.example.ClaimShieldX.extraction.client.ModelCallException.Category;
import com.example.ClaimShieldX.extraction.support.TestPdfs;
import com.example.ClaimShieldX.investigation.InvestigationApiTestBase;
import com.example.ClaimShieldX.support.TestActors;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.example.ClaimShieldX.extraction.support.TestDocuments.f;
import static com.example.ClaimShieldX.extraction.support.TestDocuments.invoice;
import static com.example.ClaimShieldX.extraction.support.TestDocuments.json;
import static com.example.ClaimShieldX.extraction.support.TestDocuments.with;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Evidence, findings, the report and the human decision, end to end: real database, storage, rules and queue; the document reader and
 * the three reasoning roles are scripted, so each test controls exactly what the models "said" and checks what Java made of it.
 */
class AnalysisApiTests extends InvestigationApiTestBase {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String ARITHMETIC = "RULE.INVOICE_ARITHMETIC";

    @Autowired FakeReasoningClient reasoning;
    @Autowired SnapshotService snapshots;

    /** The printed grand total is 1,000 above the sum of the lines, which the arithmetic rule flags. */
    private final String wrongTotalInvoice = json(with(invoice(GARAGE, VEHICLE, "12/09/2026"), b -> b.grandTotal(f("25,780.00", 1, "Grand total 25,780.00"))));
    private final String cleanInvoice = json(invoice(GARAGE, VEHICLE, "12/09/2026"));

    @BeforeEach
    void resetReasoning() {
        reasoning.reset();
    }

    private static String json(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String h1(String title, String explanation, String... refs) {
        return json(Map.of("hypotheses", List.of(Map.of("title", title, "explanation", explanation, "supportingRefs", List.of(refs),
                "checksNeeded", List.of("Ask the garage for the original invoice"), "unknowns", List.of()))));
    }

    private static String h2(String id) {
        return json(Map.of("assessments", List.of(Map.of("hypothesisId", id, "counterEvidenceRefs", List.of("CLAIM.stage"),
                "legitimateExplanations", List.of(Map.of("explanation", "A typing slip on the invoice is common.", "supportingRefs", List.of(), "checksNeeded", List.of("Compare with the carbon copy")))))));
    }

    private static String h3(String id, String support) {
        return json(Map.of("reviews", List.of(Map.of("hypothesisId", id, "support", support, "reasons", List.of("The rule result shows the difference."), "checkedRefs", List.of(ARITHMETIC)))));
    }

    private UUID investigate(TestActors actor, UUID claim, String invoiceAnswer) throws Exception {
        uploadOk(actor, claim, pdf(TestPdfs.pdf(1, ++seed)), "FINAL_INVOICE");
        model.thenReturn(invoiceAnswer);
        UUID id = createOk(actor, claim);
        drain();
        return id;
    }

    private UUID completedFlagged(TestActors actor, UUID claim, String h1, String h2, String h3) throws Exception {
        if (h1 != null) {
            reasoning.thenReturn("H1", h1);
        }
        if (h2 != null) {
            reasoning.thenReturn("H2", h2);
        }
        if (h3 != null) {
            reasoning.thenReturn("H3", h3);
        }
        UUID id = investigate(actor, claim, wrongTotalInvoice);
        assertThat(investigationStatus(id)).isEqualTo("COMPLETED");
        return id;
    }

    private String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }

    private ResultActions read(TestActors actor, String path) throws Exception {
        return mockMvc.perform(get(path).header("Authorization", actor.bearer()));
    }

    private ResultActions decide(TestActors actor, UUID investigation, String key, Object action, String rationale, Object version) throws Exception {
        var request = post("/api/v1/investigations/" + investigation + "/actions").header("Authorization", actor.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("action", action, "rationale", rationale, "reportVersion", version)));
        if (key != null) {
            request = request.header("Idempotency-Key", key);
        }
        return mockMvc.perform(request);
    }

    private List<String> findingTitles(String findingsBody, String type) {
        return JsonPath.read(findingsBody, "$[?(@.type=='" + type + "')].title");
    }

    // ---------------------------------------------------------------- the model's part

    @Test
    void aFlaggedClaimGetsAReviewedHypothesisAlongsideTheRuleFindingAndAReport() throws Exception {
        TestActors actor = actor();
        UUID claim = claimWithKnownFacts(actor);
        UUID id = completedFlagged(actor, claim, h1("Printed total differs", "The printed total differs from the sum of the lines.", ARITHMETIC), h2("H-1"), h3("H-1", "SUPPORTED"));

        String inv = investigationBody(actor, id);
        assertThat(JsonPath.<List<String>>read(inv, "$.stages[*].status")).containsExactly("OK", "DEGRADED", "OK", "OK", "OK", "OK");
        assertThat(reasoning.requests().stream().map(r -> r.role())).containsExactly("H1", "H2", "H3");

        String findings = body(read(actor, "/api/v1/investigations/" + id + "/findings").andExpect(status().isOk()));
        assertThat(findingTitles(findings, "FACT")).contains("Invoice figures do not reconcile");
        assertThat(findingTitles(findings, "HYPOTHESIS")).containsExactly("Printed total differs");
        assertThat(JsonPath.<List<String>>read(findings, "$[?(@.type=='HYPOTHESIS')].counterEvidence[0]")).isNotEmpty();
        assertThat(JsonPath.<List<String>>read(findings, "$[*].validationState")).containsOnly("VALIDATED");

        String report = body(read(actor, "/api/v1/investigations/" + id + "/report").andExpect(status().isOk()));
        assertThat(JsonPath.<String>read(report, "$.completeness")).isEqualTo("COMPLETE");
        assertThat(JsonPath.<List<String>>read(report, "$.unavailableSections")).isEmpty();
        assertThat(JsonPath.<Integer>read(report, "$.reviewPriority.score")).isEqualTo(15);
        assertThat(JsonPath.<String>read(report, "$.reviewPriority.band")).isEqualTo("LOW");
        assertThat(JsonPath.<String>read(report, "$.reviewPriority.recommendedAction")).isEqualTo("MANUAL_REVIEW");
        assertThat(JsonPath.<String>read(report, "$.reviewPriority.scale")).isEqualTo("0-100");
        assertThat(JsonPath.<String>read(report, "$.reportStatus")).isEqualTo("CURRENT");
        assertThat(JsonPath.<Integer>read(report, "$.reportVersion")).isEqualTo(1);
        assertThat(JsonPath.<String>read(report, "$.notice")).contains("not a probability").contains("not a decision");
        assertThat(report.toLowerCase()).doesNotContain("fraud");
    }

    @Test
    void theModelIsOnlyGivenTheEvidencePackageNeverTheDocumentsOrTheirWording() throws Exception {
        TestActors actor = actor();
        UUID claim = claimWithKnownFacts(actor);
        completedFlagged(actor, claim, h1("Printed total differs", "The printed total differs from the sum of the lines.", ARITHMETIC), h2("H-1"), h3("H-1", "SUPPORTED"));

        for (var request : reasoning.requests()) {
            assertThat(request.user()).contains(ARITHMETIC);
            assertThat(request.user()).doesNotContain("Front bumper").doesNotContain("Grand total 25,780.00").doesNotContain("Sub total");
            assertThat(request.system()).containsIgnoringCase("evidence");
        }
    }

    @Test
    void aHypothesisTheReviewerDoesNotSupportIsNotPublishedAndAdministratorsCanSeeWhy() throws Exception {
        TestActors admin = actor("ORG_ADMIN");
        UUID claim = claimWithKnownFacts(admin);
        UUID id = completedFlagged(admin, claim, h1("Printed total differs", "The printed total differs from the sum of the lines.", ARITHMETIC), h2("H-1"), h3("H-1", "NOT_SUPPORTED"));
        TestActors investigator = TestActors.inOrganization(fixtures, jwtSecret, admin.organizationId(), "INVESTIGATOR");

        String published = body(read(investigator, "/api/v1/investigations/" + id + "/findings"));
        assertThat(findingTitles(published, "HYPOTHESIS")).isEmpty();
        String stillHidden = body(read(investigator, "/api/v1/investigations/" + id + "/findings?includeRejected=true"));
        assertThat(findingTitles(stillHidden, "HYPOTHESIS")).as("investigators cannot ask for rejected hypotheses").isEmpty();

        String all = body(read(admin, "/api/v1/investigations/" + id + "/findings?includeRejected=true"));
        assertThat(JsonPath.<List<String>>read(all, "$[?(@.type=='HYPOTHESIS')].validationState")).containsExactly("REJECTED");
        assertThat(JsonPath.<List<String>>read(all, "$[?(@.type=='HYPOTHESIS')].rejectionReason")).containsExactly("NOT_SUPPORTED_BY_EVIDENCE");
    }

    @Test
    void aHypothesisCitingEvidenceThatDoesNotExistIsRejectedAndTheOtherRolesAreNotAsked() throws Exception {
        TestActors admin = actor("ORG_ADMIN");
        UUID claim = claimWithKnownFacts(admin);
        UUID id = completedFlagged(admin, claim, h1("Made up", "Rests on a reference that is not there.", "RULE.MADE_UP"), null, null);

        assertThat(reasoning.requests().stream().map(r -> r.role())).containsExactly("H1");
        String all = body(read(admin, "/api/v1/investigations/" + id + "/findings?includeRejected=true"));
        assertThat(JsonPath.<List<String>>read(all, "$[?(@.type=='HYPOTHESIS')]")).isEmpty();
        assertThat(findingTitles(all, "FACT")).contains("Invoice figures do not reconcile");
    }

    @Test
    void aHypothesisThatAccusesIsNeverPublished() throws Exception {
        TestActors admin = actor("ORG_ADMIN");
        UUID claim = claimWithKnownFacts(admin);
        UUID id = completedFlagged(admin, claim, h1("Possible fraud", "The garage is committing fraud.", ARITHMETIC), null, null);

        String all = body(read(admin, "/api/v1/investigations/" + id + "/findings?includeRejected=true"));
        assertThat(all.toLowerCase()).doesNotContain("committing fraud").doesNotContain("possible fraud");
        assertThat(findingTitles(all, "HYPOTHESIS")).isEmpty();
    }

    @Test
    void ifTheReviewerCannotAnswerTheReportIsPartialAndNoHypothesisIsPublished() throws Exception {
        TestActors admin = actor("ORG_ADMIN");
        UUID claim = claimWithKnownFacts(admin);
        reasoning.thenFail("H3", new ModelCallException(Category.REJECTED_INPUT, "REJECTED_INPUT", null));
        UUID id = completedFlagged(admin, claim, h1("Printed total differs", "The printed total differs from the sum of the lines.", ARITHMETIC), h2("H-1"), null);

        String report = body(read(admin, "/api/v1/investigations/" + id + "/report"));
        assertThat(JsonPath.<String>read(report, "$.completeness")).isEqualTo("PARTIAL");
        assertThat(JsonPath.<List<String>>read(report, "$.unavailableSections")).containsExactly("EVIDENCE_REVIEW");
        assertThat(JsonPath.<List<String>>read(report, "$.findings[?(@.type=='HYPOTHESIS')]")).isEmpty();
        assertThat(JsonPath.<Integer>read(report, "$.reviewPriority.score")).as("the score comes from the rules alone").isEqualTo(15);

        String all = body(read(admin, "/api/v1/investigations/" + id + "/findings?includeRejected=true"));
        assertThat(JsonPath.<List<String>>read(all, "$[?(@.type=='HYPOTHESIS')].rejectionReason")).containsExactly("REVIEW_UNAVAILABLE");
    }

    @Test
    void ifTheSuspicionRoleCannotAnswerTheRuleFindingsStillStandAndTheReportSaysWhatIsMissing() throws Exception {
        TestActors actor = actor();
        UUID claim = claimWithKnownFacts(actor);
        reasoning.thenFail("H1", new ModelCallException(Category.BLOCKED, "BLOCKED", null));
        UUID id = completedFlagged(actor, claim, null, null, null);

        String report = body(read(actor, "/api/v1/investigations/" + id + "/report"));
        assertThat(JsonPath.<String>read(report, "$.completeness")).isEqualTo("PARTIAL");
        assertThat(JsonPath.<List<String>>read(report, "$.unavailableSections")).contains("SUSPICION_ANALYSIS");
        assertThat(JsonPath.<List<String>>read(report, "$.findings[?(@.type=='FACT')].title")).contains("Invoice figures do not reconcile");
    }

    @Test
    void aClaimWithNothingFlaggedDoesNotSpendAnyModelCallsOnReasoning() throws Exception {
        TestActors actor = actor();
        UUID claim = claimWithKnownFacts(actor);
        UUID id = investigate(actor, claim, cleanInvoice);

        assertThat(investigationStatus(id)).isEqualTo("COMPLETED");
        assertThat(reasoning.callCount()).isZero();
        String report = body(read(actor, "/api/v1/investigations/" + id + "/report"));
        assertThat(JsonPath.<String>read(report, "$.completeness")).isEqualTo("COMPLETE");
        assertThat(JsonPath.<Integer>read(report, "$.reviewPriority.score")).isZero();
        assertThat(JsonPath.<String>read(report, "$.reviewPriority.recommendedAction")).as("only an invoice was uploaded, so checks are missing").isEqualTo("REQUEST_MORE_EVIDENCE");
        assertThat(JsonPath.<List<String>>read(report, "$.findings[?(@.type=='UNKNOWN')]")).isNotEmpty();
    }

    // ---------------------------------------------------------------- reading what was produced

    @Test
    void evidenceAndGraphAreServedFromTheFrozenSnapshotWithoutSourceQuotes() throws Exception {
        TestActors actor = actor();
        UUID claim = claimWithKnownFacts(actor);
        UUID id = completedFlagged(actor, claim, null, null, null);

        String evidence = body(read(actor, "/api/v1/investigations/" + id + "/evidence").andExpect(status().isOk()));
        assertThat(JsonPath.<Integer>read(evidence, "$.itemCount")).isEqualTo(JsonPath.<List<?>>read(evidence, "$.items").size());
        assertThat(JsonPath.<List<String>>read(evidence, "$.items[*].ref")).contains("CLAIM.stage", ARITHMETIC).doesNotHaveDuplicates();
        assertThat(JsonPath.<List<String>>read(evidence, "$.items[?(@.ref=='CLAIM.stage')].state")).containsExactly("VERIFIED_FACT");
        assertThat(JsonPath.<List<String>>read(evidence, "$.items[?(@.ref=='" + ARITHMETIC + "')].state")).containsExactly("DETERMINISTIC_RESULT");
        assertThat(JsonPath.<List<String>>read(evidence, "$.items[?(@.ref=~/LINES\\..*/)].ref")).as("the person sees the line items; the model does not").hasSize(1);
        assertThat(evidence).as("source quotes are not part of the evidence").doesNotContain("Grand total 25,780.00").doesNotContain("Sub total");

        String graph = body(read(actor, "/api/v1/investigations/" + id + "/graph").andExpect(status().isOk()));
        assertThat(JsonPath.<List<String>>read(graph, "$.nodes[*].id")).containsExactly("CLAIM");
        assertThat(JsonPath.<List<Object>>read(graph, "$.edges")).isEmpty();
    }

    @Test
    void theSnapshotIsFrozenOnceAndFreezingAgainChangesNothing() throws Exception {
        TestActors actor = actor();
        UUID claim = claimWithKnownFacts(actor);
        UUID id = completedFlagged(actor, claim, null, null, null);
        String before = body(read(actor, "/api/v1/investigations/" + id + "/evidence"));
        UUID ruleRun = UUID.fromString(JsonPath.read(before, "$.ruleRunId"));

        UUID again = snapshots.freeze(actor.organizationId(), claim, id, ruleRun);

        assertThat(again).isEqualTo(UUID.fromString(JsonPath.read(before, "$.snapshotId")));
        assertThat(body(read(actor, "/api/v1/investigations/" + id + "/evidence"))).isEqualTo(before);
        assertThat(jdbc.queryForObject("select count(*) from evidence_snapshots where investigation_id = ?", Integer.class, id)).isEqualTo(1);
    }

    @Test
    void theLatestReportOfAClaimIsTheNewestInvestigationsAndEarlierOnesBecomeStale() throws Exception {
        TestActors actor = actor();
        UUID claim = claimWithKnownFacts(actor);
        UUID first = completedFlagged(actor, claim, null, null, null);
        model.thenReturn(wrongTotalInvoice);
        uploadOk(actor, claim, pdf(TestPdfs.pdf(1, ++seed)), "GARAGE_ESTIMATE");
        UUID second = createOk(actor, claim);
        drain();
        assertThat(investigationStatus(second)).isEqualTo("COMPLETED");

        String latest = body(read(actor, "/api/v1/claims/" + claim + "/reports/latest").andExpect(status().isOk()));
        assertThat(JsonPath.<Integer>read(latest, "$.reportVersion")).isEqualTo(2);
        assertThat(JsonPath.<String>read(latest, "$.investigationId")).isEqualTo(second.toString());
        assertThat(JsonPath.<String>read(latest, "$.reportStatus")).isEqualTo("CURRENT");

        String older = body(read(actor, "/api/v1/investigations/" + first + "/report"));
        assertThat(JsonPath.<Integer>read(older, "$.reportVersion")).isEqualTo(1);
        assertThat(JsonPath.<String>read(older, "$.reportStatus")).as("a document arrived after the first snapshot").isEqualTo("STALE");
        assertThat(JsonPath.<String>read(investigationBody(actor, first), "$.reportStatus")).isEqualTo("STALE");
    }

    // ---------------------------------------------------------------- the person's decision

    @Test
    void aPersonRecordsADecisionOnTheReportAndItIsAppendOnlyAndAudited() throws Exception {
        TestActors actor = actor();
        UUID claim = claimWithKnownFacts(actor);
        UUID id = completedFlagged(actor, claim, null, null, null);

        String key = newKey();
        String created = body(decide(actor, id, key, "REQUEST_MORE_EVIDENCE", "Asked the garage for the original invoice copy.", 1).andExpect(status().isCreated()));
        assertThat(JsonPath.<String>read(created, "$.action")).isEqualTo("REQUEST_MORE_EVIDENCE");
        assertThat(JsonPath.<Integer>read(created, "$.reportVersion")).isEqualTo(1);
        assertThat(JsonPath.<Boolean>read(created, "$.reportWasStale")).isFalse();
        assertThat(JsonPath.<String>read(created, "$.recommendedAction")).isEqualTo("MANUAL_REVIEW");
        assertThat(JsonPath.<Integer>read(created, "$.score")).isEqualTo(15);
        assertThat(JsonPath.<String>read(created, "$.createdBy")).isEqualTo(actor.userId().toString());

        decide(actor, id, key, "REQUEST_MORE_EVIDENCE", "Asked the garage for the original invoice copy.", 1).andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(JsonPath.<String>read(created, "$.id")));
        decide(actor, id, newKey(), "DEFER_REVIEW", "Waiting for the garage to answer before deciding.", 1).andExpect(status().isCreated());

        String actions = body(read(actor, "/api/v1/investigations/" + id + "/actions").andExpect(status().isOk()));
        assertThat(JsonPath.<List<String>>read(actions, "$[*].action")).containsExactly("REQUEST_MORE_EVIDENCE", "DEFER_REVIEW");
        assertThat(JsonPath.<String>read(body(read(actor, "/api/v1/investigations/" + id + "/report")), "$.latestAction.action")).isEqualTo("DEFER_REVIEW");

        String audit = body(read(actor, "/api/v1/investigations/" + id + "/audit"));
        List<String> types = JsonPath.read(audit, "$.items[*].eventType");
        assertThat(types).contains("INVESTIGATION_CREATED", "REPORT_GENERATED", "INVESTIGATION_COMPLETED");
        assertThat(types.stream().filter("REVIEW_ACTION_RECORDED"::equals)).hasSize(2);
        assertThat(audit).doesNotContain("Asked the garage").as("the rationale stays in the decision, not in the audit trail");
    }

    @Test
    void aDecisionMustNameARealActionTheCurrentReportVersionAndAReasonedRationale() throws Exception {
        TestActors actor = actor();
        UUID claim = claimWithKnownFacts(actor);
        UUID id = completedFlagged(actor, claim, null, null, null);

        decide(actor, id, null, "DEFER_REVIEW", "Waiting for the garage to answer.", 1).andExpect(status().isBadRequest());
        decide(actor, id, newKey(), "DEFER_REVIEW", "short", 1).andExpect(status().isBadRequest());
        decide(actor, id, newKey(), "DEFER_REVIEW", "x".repeat(2001), 1).andExpect(status().isBadRequest());
        decide(actor, id, newKey(), "REJECT_CLAIM", "The claim should be rejected outright.", 1).andExpect(status().isBadRequest());
        decide(actor, id, newKey(), "SETTLE_CLAIM", "The claim should be settled right away.", 1).andExpect(status().isBadRequest());
        decide(actor, id, newKey(), "DEFER_REVIEW", "Waiting for the garage to answer.", 7).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REPORT_VERSION_MISMATCH"));
        assertThat(jdbc.queryForObject("select count(*) from review_actions where investigation_id = ?", Integer.class, id)).isZero();
    }

    @Test
    void aDecisionOnAReportThatNewerEvidenceHasOvertakenIsRecordedAsMadeOnAStaleReport() throws Exception {
        TestActors actor = actor();
        UUID claim = claimWithKnownFacts(actor);
        UUID id = completedFlagged(actor, claim, null, null, null);
        uploadOk(actor, claim, pdf(TestPdfs.pdf(1, ++seed)), "CLAIM_FORM");

        assertThat(JsonPath.<String>read(body(read(actor, "/api/v1/investigations/" + id + "/report")), "$.reportStatus")).isEqualTo("STALE");
        decide(actor, id, newKey(), "ESCALATE_FOR_FURTHER_INVESTIGATION", "Escalating while the new document is read.", 1).andExpect(status().isCreated())
                .andExpect(jsonPath("$.reportWasStale").value(true));
    }

    @Test
    void noDecisionCanBeRecordedBeforeThereIsAReport() throws Exception {
        TestActors actor = actor();
        UUID claim = claimWithKnownFacts(actor);
        uploadOk(actor, claim, pdf(TestPdfs.pdf(1, ++seed)), "FINAL_INVOICE");
        UUID id = createOk(actor, claim);

        decide(actor, id, newKey(), "DEFER_REVIEW", "Waiting for the garage to answer.", 1).andExpect(status().isConflict());
        read(actor, "/api/v1/investigations/" + id + "/report").andExpect(status().isNotFound());
        read(actor, "/api/v1/investigations/" + id + "/evidence").andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- who may see what

    @Test
    void anotherOrganizationSeesNothingOfAnInvestigationAndCannotDecideOnIt() throws Exception {
        TestActors owner = actor();
        UUID claim = claimWithKnownFacts(owner);
        UUID id = completedFlagged(owner, claim, null, null, null);
        TestActors stranger = actor();

        for (String path : List.of("evidence", "graph", "findings", "report", "actions", "audit")) {
            read(stranger, "/api/v1/investigations/" + id + "/" + path).andExpect(status().isNotFound());
        }
        read(stranger, "/api/v1/claims/" + claim + "/reports/latest").andExpect(status().isNotFound());
        decide(stranger, id, newKey(), "DEFER_REVIEW", "Waiting for the garage to answer.", 1).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select count(*) from review_actions where investigation_id = ?", Integer.class, id)).isZero();
    }

    @Test
    void everythingNeedsASignedInUser() throws Exception {
        UUID id = UUID.randomUUID();
        for (String path : List.of("evidence", "graph", "findings", "report", "actions", "audit")) {
            mockMvc.perform(get("/api/v1/investigations/" + id + "/" + path)).andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/api/v1/investigations/" + id + "/actions").header("Idempotency-Key", newKey()).contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isUnauthorized());
    }
}
