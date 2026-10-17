package com.example.ClaimShieldX.extraction.service;

import com.example.ClaimShieldX.documents.DocumentApiTestBase;
import com.example.ClaimShieldX.extraction.support.FakeExtractionModelClient;
import com.example.ClaimShieldX.support.TestActors;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Real database, real storage and the real HTTP stack; only the model is scripted. */
public abstract class ExtractionApiTestBase extends DocumentApiTestBase {
    public static final String GARAGE = "GARAGE-DEMO-10";
    public static final String VEHICLE = "VEH-TOKEN-1001";
    /** 10:30 on 10 September 2026 in India. */
    public static final Instant INCIDENT = Instant.parse("2026-09-10T05:00:00Z");

    @Autowired protected FakeExtractionModelClient model;

    @BeforeEach
    void resetModel() {
        model.reset();
    }

    protected UUID claimWithKnownFacts(TestActors actor) {
        return fixtures.claim(actor.organizationId(), "CLM-" + UUID.randomUUID(), "FINAL_INVOICE_RECEIVED", "COLLISION", GARAGE, VEHICLE, INCIDENT);
    }

    protected static String url(UUID claim, UUID document) {
        return "/api/v1/claims/" + claim + "/documents/" + document + "/extraction";
    }

    protected ResultActions startExtraction(TestActors actor, UUID claim, UUID document) throws Exception {
        return mockMvc.perform(post(url(claim, document)).header("Authorization", actor.bearer()));
    }

    protected ResultActions readExtraction(TestActors actor, UUID claim, UUID document) throws Exception {
        return mockMvc.perform(get(url(claim, document)).header("Authorization", actor.bearer()));
    }

    protected String documentStatus(UUID document) {
        return jdbc.queryForObject("select status from documents where id = ?", String.class, document);
    }

    protected int extractionRows(UUID document) {
        return jdbc.queryForObject("select count(*) from document_extractions where document_id = ?", Integer.class, document);
    }
}
