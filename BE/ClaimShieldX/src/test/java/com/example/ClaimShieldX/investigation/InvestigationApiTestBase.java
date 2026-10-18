package com.example.ClaimShieldX.investigation;

import com.example.ClaimShieldX.extraction.service.ExtractionApiTestBase;
import com.example.ClaimShieldX.investigation.work.InvestigationRunner;
import com.example.ClaimShieldX.support.TestActors;
import com.jayway.jsonpath.JsonPath;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Real database, storage and HTTP stack, the scripted model, and the real stage handlers. The worker thread is off; tests call {@link #drain()}. */
public abstract class InvestigationApiTestBase extends ExtractionApiTestBase {
    protected static final String WORKER = "test-worker";

    @Autowired protected InvestigationRunner runner;

    protected int seed = 100;

    /** See InvestigationQueueTestBase: the queue is global, so a killed earlier run must not leave work for this one. */
    @org.junit.jupiter.api.BeforeEach
    void startFromAnEmptyQueue() {
        jdbc.update("delete from investigation_stages");
        jdbc.update("delete from investigations");
    }

    protected ResultActions createInvestigation(TestActors actor, UUID claim, String key, String body) throws Exception {
        var request = post("/api/v1/claims/" + claim + "/investigations").header("Authorization", actor.bearer());
        if (key != null) {
            request = request.header("Idempotency-Key", key);
        }
        if (body != null) {
            request = request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }

    protected ResultActions createInvestigation(TestActors actor, UUID claim) throws Exception {
        return createInvestigation(actor, claim, newKey(), null);
    }

    protected UUID createOk(TestActors actor, UUID claim) throws Exception {
        String body = createInvestigation(actor, claim).andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    protected ResultActions getInvestigation(TestActors actor, UUID id) throws Exception {
        return mockMvc.perform(get("/api/v1/investigations/" + id).header("Authorization", actor.bearer()));
    }

    protected String investigationBody(TestActors actor, UUID id) throws Exception {
        return getInvestigation(actor, id).andReturn().getResponse().getContentAsString();
    }

    protected ResultActions postAction(TestActors actor, UUID id, String action, String key) throws Exception {
        var request = post("/api/v1/investigations/" + id + "/" + action).header("Authorization", actor.bearer());
        if (key != null) {
            request = request.header("Idempotency-Key", key);
        }
        return mockMvc.perform(request);
    }

    protected ResultActions listInvestigations(TestActors actor, UUID claim, String... params) throws Exception {
        var request = get("/api/v1/claims/" + claim + "/investigations").header("Authorization", actor.bearer());
        for (int i = 0; i < params.length; i += 2) {
            request = request.param(params[i], params[i + 1]);
        }
        return mockMvc.perform(request);
    }

    /** Works through everything that is due, the way the background worker would, and returns how many investigations it picked up. */
    protected int drain() {
        int worked = 0;
        while (runner.runNext(WORKER)) {
            worked++;
            if (worked > 50) {
                throw new AssertionError("The queue never emptied");
            }
        }
        return worked;
    }

    protected String investigationStatus(UUID id) {
        return jdbc.queryForObject("select status from investigations where id = ?", String.class, id);
    }

    /** Makes a sleeping or waiting investigation due now, standing in for the passage of time. */
    protected void makeDue(UUID id) {
        jdbc.update("update investigations set next_attempt_at = ? where id = ?", Timestamp.from(Instant.now().minusSeconds(1)), id);
    }

    protected String stageStatus(UUID id, String stage) {
        var found = jdbc.queryForList("select status from investigation_stages where investigation_id = ? and stage = ?", String.class, id, stage);
        return found.isEmpty() ? null : found.get(0);
    }
}
