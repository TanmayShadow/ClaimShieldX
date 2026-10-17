package com.example.ClaimShieldX.readiness;

import com.example.ClaimShieldX.documents.DocumentApiTestBase;
import com.example.ClaimShieldX.documents.model.DocumentType;
import com.example.ClaimShieldX.support.TestActors;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /api/v1/claims: tenant scoping, filters, paging and the readiness shown on each row. */
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class ClaimListApiTests extends DocumentApiTestBase {
    private static final String URL = "/api/v1/claims";

    @Autowired private EntityManagerFactory entityManagerFactory;

    private ResultActions list(TestActors actor, String... params) throws Exception {
        var request = get(URL).header("Authorization", actor.bearer());
        for (int i = 0; i < params.length; i += 2) {
            request = request.param(params[i], params[i + 1]);
        }
        return mockMvc.perform(request);
    }

    private UUID claim(TestActors actor, String internalId, String stage, Instant createdAt) {
        UUID id = fixtures.claim(actor.organizationId(), internalId, stage, "COLLISION");
        jdbc.update("update claims set created_at = ? where id = ?", Timestamp.from(createdAt), id);
        return id;
    }

    // ------------------------------------------------------------ access and tenancy

    @Test
    void anonymousRequestsAreRejected() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void onlyTheCallersOwnOrganizationsClaimsAreListed() throws Exception {
        TestActors mine = actor();
        TestActors other = actor();
        UUID own = claimFor(mine);
        UUID foreign = claimFor(other);

        list(mine).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(own.toString()))
                .andExpect(jsonPath("$.items[*].id", not(hasItem(foreign.toString()))));
    }

    @Test
    void anOrganizationWithNoClaimsGetsAnEmptyPageNotAnError() throws Exception {
        list(actor()).andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0)).andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    void aClientCannotChooseAnotherOrganization() throws Exception {
        TestActors mine = actor();
        TestActors other = actor();
        claimFor(other);

        list(mine, "organizationId", other.organizationId().toString(), "organization", other.organizationId().toString())
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }

    // ------------------------------------------------------------ the row

    @Test
    void aRowIsASummaryWithoutPersonalOrFinancialDetail() throws Exception {
        TestActors actor = actor();
        UUID claim = claimFor(actor);

        String body = list(actor).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(claim.toString()))
                .andExpect(jsonPath("$.items[0].internalId").isNotEmpty())
                .andExpect(jsonPath("$.items[0].currentStage").value("FINAL_INVOICE_RECEIVED"))
                .andExpect(jsonPath("$.items[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$.items[0].readiness.level").value("NOT_READY"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("policy").doesNotContain("vehicle").doesNotContain("garage")
                .doesNotContain("financial").doesNotContain("claimant").doesNotContain(actor.organizationId().toString());
    }

    @Test
    void eachRowShowsItsOwnReadinessAndCounts() throws Exception {
        TestActors actor = actor();
        UUID empty = claim(actor, "A-EMPTY", "FINAL_INVOICE_RECEIVED", Instant.now().minusSeconds(30));
        UUID invoiceOnly = claim(actor, "B-INVOICE", "FINAL_INVOICE_RECEIVED", Instant.now().minusSeconds(20));
        UUID complete = claim(actor, "C-FNOL", "FNOL", Instant.now().minusSeconds(10));
        fixtures.document(actor.organizationId(), invoiceOnly, actor.userId(), DocumentType.FINAL_INVOICE, "RECEIVED", Instant.now(), false);
        fixtures.document(actor.organizationId(), complete, actor.userId(), DocumentType.CLAIM_FORM, "VERIFIED", Instant.now(), false);
        fixtures.document(actor.organizationId(), complete, actor.userId(), DocumentType.POLICY_RECORD, "VERIFIED", Instant.now(), false);

        list(actor, "sort", "internalId,asc").andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(empty.toString()))
                .andExpect(jsonPath("$.items[0].readiness.level").value("NOT_READY"))
                .andExpect(jsonPath("$.items[0].readiness.blockerCount").value(1))
                .andExpect(jsonPath("$.items[1].id").value(invoiceOnly.toString()))
                .andExpect(jsonPath("$.items[1].readiness.level").value("READY_WITH_WARNINGS"))
                .andExpect(jsonPath("$.items[1].readiness.blockerCount").value(0))
                .andExpect(jsonPath("$.items[1].readiness.warningCount").value(greaterThan(0)))
                .andExpect(jsonPath("$.items[2].id").value(complete.toString()))
                .andExpect(jsonPath("$.items[2].readiness.level").value("READY"))
                .andExpect(jsonPath("$.items[2].readiness.warningCount").value(0));
    }

    @Test
    void theRowReadinessMatchesTheFullChecklist() throws Exception {
        TestActors actor = actor();
        UUID claim = claimFor(actor);
        fixtures.document(actor.organizationId(), claim, actor.userId(), DocumentType.FINAL_INVOICE, "NEEDS_REVIEW", Instant.now(), false);

        String full = mockMvc.perform(get(URL + "/" + claim + "/document-requirements").header("Authorization", actor.bearer()))
                .andReturn().getResponse().getContentAsString();
        int blockers = com.jayway.jsonpath.JsonPath.<List<?>>read(full, "$.blockers").size();
        int warnings = com.jayway.jsonpath.JsonPath.<List<?>>read(full, "$.warnings").size();
        String level = com.jayway.jsonpath.JsonPath.read(full, "$.readiness");

        list(actor).andExpect(jsonPath("$.items[0].readiness.level").value(level))
                .andExpect(jsonPath("$.items[0].readiness.blockerCount").value(blockers))
                .andExpect(jsonPath("$.items[0].readiness.warningCount").value(warnings));
    }

    // ------------------------------------------------------------ filters

    @Test
    void filtersByStage() throws Exception {
        TestActors actor = actor();
        UUID fnol = claim(actor, "F-1", "FNOL", Instant.now());
        claim(actor, "E-1", "ESTIMATE_RECEIVED", Instant.now());

        list(actor, "stage", "FNOL").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(fnol.toString()));
        list(actor).andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void archivedClaimsAreHiddenUnlessAskedFor() throws Exception {
        TestActors actor = actor();
        UUID active = claim(actor, "ACTIVE-1", "FNOL", Instant.now());
        UUID archived = claim(actor, "ARCHIVED-1", "FNOL", Instant.now());
        jdbc.update("update claims set is_active = false where id = ?", archived);

        list(actor).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items[0].id").value(active.toString()));
        list(actor, "active", "false").andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items[0].id").value(archived.toString()));
        list(actor, "active", "true").andExpect(jsonPath("$.items[0].id").value(active.toString()));
    }

    @Test
    void theFiltersCombine() throws Exception {
        TestActors actor = actor();
        UUID wanted = claim(actor, "W-1", "FNOL", Instant.now());
        UUID archivedFnol = claim(actor, "W-2", "FNOL", Instant.now());
        claim(actor, "W-3", "ESTIMATE_RECEIVED", Instant.now());
        jdbc.update("update claims set is_active = false where id = ?", archivedFnol);

        list(actor, "stage", "FNOL", "active", "true").andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(wanted.toString()));
    }

    // ------------------------------------------------------------ paging and sorting

    @Test
    void newestFirstByDefaultWithAStableTotal() throws Exception {
        TestActors actor = actor();
        UUID oldest = claim(actor, "N-1", "FNOL", Instant.now().minusSeconds(300));
        UUID middle = claim(actor, "N-2", "FNOL", Instant.now().minusSeconds(200));
        UUID newest = claim(actor, "N-3", "FNOL", Instant.now().minusSeconds(100));

        list(actor).andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.items[*].id", contains(newest.toString(), middle.toString(), oldest.toString())));
        list(actor, "sort", "createdAt,asc").andExpect(jsonPath("$.items[*].id", contains(oldest.toString(), middle.toString(), newest.toString())));
    }

    @Test
    void pagingNeitherRepeatsNorSkipsAClaim() throws Exception {
        TestActors actor = actor();
        List<UUID> created = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            created.add(claim(actor, "P-" + i, "FNOL", Instant.now().minusSeconds(100 - i)));
        }

        List<String> seen = new ArrayList<>();
        for (int page = 0; page < 3; page++) {
            String body = list(actor, "size", "2", "page", String.valueOf(page)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(5)).andExpect(jsonPath("$.totalPages").value(3))
                    .andReturn().getResponse().getContentAsString();
            seen.addAll(com.jayway.jsonpath.JsonPath.<List<String>>read(body, "$.items[*].id"));
        }

        assertThat(seen).hasSize(5).doesNotHaveDuplicates()
                .containsExactlyInAnyOrderElementsOf(created.stream().map(UUID::toString).toList());
    }

    @Test
    void sortsByInternalId() throws Exception {
        TestActors actor = actor();
        claim(actor, "ZZ-9", "FNOL", Instant.now());
        claim(actor, "AA-1", "FNOL", Instant.now());

        list(actor, "sort", "internalId,asc").andExpect(jsonPath("$.items[0].internalId").value("AA-1"));
        list(actor, "sort", "internalId,desc").andExpect(jsonPath("$.items[0].internalId").value("ZZ-9"));
    }

    @Test
    void badParametersAreValidationErrorsThatDoNotEchoTheValue() throws Exception {
        TestActors actor = actor();
        claimFor(actor);

        for (String[] params : new String[][]{
                {"stage", "NOT_A_STAGE"}, {"sort", "organizationId,asc"}, {"sort", "createdAt,sideways"}, {"size", "101"},
                {"size", "0"}, {"page", "-1"}, {"active", "maybe"}, {"page", "abc"}}) {
            String body = list(actor, params).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andReturn().getResponse().getContentAsString();
            assertThat(body).as(String.join("=", params)).doesNotContain("NOT_A_STAGE").doesNotContain("maybe");
        }
    }

    // ------------------------------------------------------------ cost

    @Test
    void theNumberOfDatabaseStatementsDoesNotGrowWithTheNumberOfClaims() throws Exception {
        TestActors small = actor();
        TestActors large = actor();
        for (int i = 0; i < 2; i++) {
            UUID claim = claim(small, "S-" + i, "FINAL_INVOICE_RECEIVED", Instant.now());
            fixtures.document(small.organizationId(), claim, small.userId(), DocumentType.FINAL_INVOICE, "RECEIVED", Instant.now(), false);
        }
        for (int i = 0; i < 25; i++) {
            UUID claim = claim(large, "L-" + i, "FINAL_INVOICE_RECEIVED", Instant.now());
            fixtures.document(large.organizationId(), claim, large.userId(), DocumentType.FINAL_INVOICE, "RECEIVED", Instant.now(), false);
            fixtures.document(large.organizationId(), claim, large.userId(), DocumentType.CLAIM_FORM, "VERIFIED", Instant.now(), false);
        }

        // Both pages are full, so both run the same page-count query; only the number of rows on the page differs.
        long forTwo = statementsFor(small, 2, 2);
        long forTwenty = statementsFor(large, 20, 20);

        assertThat(forTwenty).as("statements for a page of 20 claims vs a page of 2").isEqualTo(forTwo);
    }

    private long statementsFor(TestActors actor, int pageSize, int expectedRows) throws Exception {
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        list(actor, "size", String.valueOf(pageSize)).andExpect(status().isOk());
        statistics.clear();
        list(actor, "size", String.valueOf(pageSize)).andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(expectedRows)));
        return statistics.getPrepareStatementCount();
    }
}
