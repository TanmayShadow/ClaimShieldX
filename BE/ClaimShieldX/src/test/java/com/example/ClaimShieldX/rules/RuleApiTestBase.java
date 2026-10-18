package com.example.ClaimShieldX.rules;

import com.example.ClaimShieldX.history.HistoryApiTestBase;
import com.example.ClaimShieldX.rules.engine.Inputs;
import com.example.ClaimShieldX.support.TestActors;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.jayway.jsonpath.JsonPath;
import org.springframework.test.web.servlet.ResultActions;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Real database and HTTP stack, with claims, documents and stored extractions built from the same fixtures the rule unit tests use. */
public abstract class RuleApiTestBase extends HistoryApiTestBase {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** A claim with the dates and estimate the rules compare, matching {@link Inputs#claim()}. */
    protected UUID ruleClaim(TestActors actor, String internalId) {
        return ruleClaim(actor, internalId, "FINAL_INVOICE_RECEIVED", "COLLISION", "VEH-1", "GAR-1");
    }

    protected UUID ruleClaim(TestActors actor, String internalId, String stage, String incidentType, String vehicle, String garage) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into claims(id, organization_id, internal_id, current_stage, policy_token, garage_id, vehicle_token, incident_type,
                                   incident_at, reported_at, currency, initial_estimate_amount, policy_start_date, policy_end_date)
                values (?, ?, ?, ?, 'POL-1', ?, ?, ?, ?, ?, 'INR', 15000.00, ?, ?)
                """, id, actor.organizationId(), internalId, stage, garage, vehicle, incidentType, Timestamp.from(Inputs.INCIDENT),
                Timestamp.from(Instant.parse("2026-09-11T05:00:00Z")), Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")), Timestamp.from(Instant.parse("2026-12-31T00:00:00Z")));
        return id;
    }

    /** Stores the document and, unless the spec says it was never read, its successful extraction. Returns the document's id. */
    protected UUID attach(TestActors actor, UUID claimId, Inputs.Doc spec) {
        UUID documentId = UUID.randomUUID();
        jdbc.update("""
                insert into documents(id, organization_id, claim_id, document_type, file_name, content_type, size_bytes, sha256, object_key,
                                      uploaded_by, status, received_at, superseded)
                values (?, ?, ?, ?, 'file.pdf', 'application/pdf', 1024, ?, ?, ?, ?, ?, ?)
                """, documentId, actor.organizationId(), claimId, spec.type.name(), spec.sha256, "obj/" + documentId, actor.userId(), spec.status.name(),
                Timestamp.from(spec.receivedAt), spec.superseded);
        if (spec.extracted) {
            try {
                String result = JSON.writeValueAsString(spec.facts().extraction().result());
                jdbc.update("""
                        insert into document_extractions(id, organization_id, claim_id, document_id, document_type, attempt, status, schema_version,
                                                         prompt_version, model_id, result, model_calls, duration_ms)
                        values (?, ?, ?, ?, ?, 1, 'SUCCEEDED', 'itemized-cost-2', '1', 'test-model', ?::jsonb, 1, 100)
                        """, UUID.randomUUID(), actor.organizationId(), claimId, documentId, spec.type.name(), result);
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                throw new IllegalStateException(e);
            }
        }
        return documentId;
    }

    /** A claim whose invoice and estimate reconcile and agree with it, so every document rule passes. */
    protected UUID cleanClaim(TestActors actor, String internalId) {
        UUID claim = ruleClaim(actor, internalId);
        attach(actor, claim, Inputs.invoice());
        attach(actor, claim, Inputs.estimate());
        return claim;
    }

    protected ResultActions runRules(TestActors actor, UUID claimId) throws Exception {
        return mockMvc.perform(post("/api/v1/claims/" + claimId + "/rule-runs").header("Authorization", actor.bearer()));
    }

    protected ResultActions latestRun(TestActors actor, UUID claimId) throws Exception {
        return mockMvc.perform(get("/api/v1/claims/" + claimId + "/rule-runs/latest").header("Authorization", actor.bearer()));
    }

    protected ResultActions getRun(TestActors actor, UUID claimId, UUID runId) throws Exception {
        return mockMvc.perform(get("/api/v1/claims/" + claimId + "/rule-runs/" + runId).header("Authorization", actor.bearer()));
    }

    protected ResultActions listRuns(TestActors actor, UUID claimId, String... params) throws Exception {
        var request = get("/api/v1/claims/" + claimId + "/rule-runs").header("Authorization", actor.bearer());
        for (int i = 0; i < params.length; i += 2) {
            request = request.param(params[i], params[i + 1]);
        }
        return mockMvc.perform(request);
    }

    protected static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }

    /** Runs the rules and returns the run's response body, failing if the request was not 200 or 201. */
    protected String runOk(TestActors actor, UUID claimId) throws Exception {
        var response = runRules(actor, claimId).andReturn().getResponse();
        if (response.getStatus() != 200 && response.getStatus() != 201) {
            throw new AssertionError("Expected 200 or 201 but got " + response.getStatus() + ": " + response.getContentAsString());
        }
        return response.getContentAsString();
    }

    protected static Map<String, Object> result(String runBody, String ruleId) {
        List<Map<String, Object>> found = JsonPath.read(runBody, "$.results[?(@.ruleId=='" + ruleId + "')]");
        if (found.size() != 1) {
            throw new AssertionError("Expected one result for " + ruleId + " but found " + found.size());
        }
        return found.get(0);
    }

    protected static String outcome(String runBody, String ruleId) {
        return (String) result(runBody, ruleId).get("outcome");
    }

    protected int runCount(UUID claimId) {
        return jdbc.queryForObject("select count(*) from rule_runs where claim_id = ?", Integer.class, claimId);
    }
}
