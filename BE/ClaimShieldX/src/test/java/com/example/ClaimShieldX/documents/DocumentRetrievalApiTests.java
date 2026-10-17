package com.example.ClaimShieldX.documents;

import com.example.ClaimShieldX.support.TestActors;
import com.example.ClaimShieldX.support.TestFiles;
import com.example.ClaimShieldX.support.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DocumentRetrievalApiTests extends DocumentApiTestBase {
    private String listUrl(UUID claim) {
        return "/api/v1/claims/" + claim + "/documents";
    }

    // ---------------------------------------------------------------- list

    @Test
    void listsDocumentsNewestFirstInAPagedEnvelope() throws Exception {
        TestActors actor = actor();
        UUID claim = claimFor(actor);
        UUID first = uploadOk(actor, claim, pdf(TestFiles.pdf(1)), "CLAIM_FORM");
        Thread.sleep(15);
        UUID second = uploadOk(actor, claim, pdf(TestFiles.pdf(2)), "FINAL_INVOICE");

        mockMvc.perform(get(listUrl(claim)).header("Authorization", actor.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.items[0].id").value(second.toString()))
                .andExpect(jsonPath("$.items[1].id").value(first.toString()))
                .andExpect(jsonPath("$.items[0].objectKey").doesNotExist());
    }

    @Test
    void pagingSplitsTheListWithoutRepeatingOrSkipping() throws Exception {
        TestActors actor = actor();
        UUID claim = claimFor(actor);
        for (int i = 0; i < 5; i++) {
            uploadOk(actor, claim, pdf(TestFiles.pdf(100 + i)), "OTHER");
        }

        MvcResult pageOne = mockMvc.perform(get(listUrl(claim)).param("size", "2").param("page", "0").header("Authorization", actor.bearer()))
                .andExpect(jsonPath("$.items", hasSize(2))).andExpect(jsonPath("$.totalElements").value(5)).andExpect(jsonPath("$.totalPages").value(3)).andReturn();
        MvcResult pageTwo = mockMvc.perform(get(listUrl(claim)).param("size", "2").param("page", "1").header("Authorization", actor.bearer()))
                .andExpect(jsonPath("$.items", hasSize(2))).andReturn();
        MvcResult pageThree = mockMvc.perform(get(listUrl(claim)).param("size", "2").param("page", "2").header("Authorization", actor.bearer()))
                .andExpect(jsonPath("$.items", hasSize(1))).andReturn();

        java.util.Set<String> ids = new java.util.HashSet<>();
        for (MvcResult page : new MvcResult[]{pageOne, pageTwo, pageThree}) {
            ids.addAll(com.jayway.jsonpath.JsonPath.<java.util.List<String>>read(page.getResponse().getContentAsString(), "$.items[*].id"));
        }
        assertThat(ids).hasSize(5);
    }

    @Test
    void aPageBeyondTheEndIsEmptyNotAnError() throws Exception {
        TestActors actor = actor();
        UUID claim = claimFor(actor);
        uploadOk(actor, claim, pdf(TestFiles.pdf(1)), "OTHER");

        mockMvc.perform(get(listUrl(claim)).param("page", "9").header("Authorization", actor.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(0))).andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void sortingByAnAllowedFieldWorksAndOtherFieldsAreRejected() throws Exception {
        TestActors actor = actor();
        UUID claim = claimFor(actor);
        uploadOk(actor, claim, file("b-second.pdf", "application/pdf", TestFiles.pdf(1)), "OTHER");
        uploadOk(actor, claim, file("a-first.pdf", "application/pdf", TestFiles.pdf(2)), "OTHER");

        mockMvc.perform(get(listUrl(claim)).param("sort", "fileName,asc").header("Authorization", actor.bearer()))
                .andExpect(jsonPath("$.items[0].fileName").value("a-first.pdf"));
        mockMvc.perform(get(listUrl(claim)).param("sort", "fileName,desc").header("Authorization", actor.bearer()))
                .andExpect(jsonPath("$.items[0].fileName").value("b-second.pdf"));
        for (String bad : new String[]{"objectKey", "organizationId,asc", "fileName;drop table documents"}) {
            mockMvc.perform(get(listUrl(claim)).param("sort", bad).header("Authorization", actor.bearer()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors[*].field", hasItem("sort")));
        }
    }

    @Test
    void anOversizedOrNegativePageRequestIsRejected() throws Exception {
        TestActors actor = actor();
        UUID claim = claimFor(actor);

        mockMvc.perform(get(listUrl(claim)).param("size", "101").header("Authorization", actor.bearer()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[*].field", hasItem("size")));
        mockMvc.perform(get(listUrl(claim)).param("page", "-1").header("Authorization", actor.bearer()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[*].field", hasItem("page")));
        mockMvc.perform(get(listUrl(claim)).param("size", "abc").header("Authorization", actor.bearer()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void theListShowsSupersededDocumentsFlagged() throws Exception {
        TestActors actor = actor();
        UUID claim = claimFor(actor);
        UUID first = uploadOk(actor, claim, pdf(TestFiles.pdf(1)), "FINAL_INVOICE");
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(org.springframework.http.HttpMethod.POST, listUrl(claim))
                .file(pdf(TestFiles.pdf(2))).param("documentType", "FINAL_INVOICE").param("supersedesDocumentId", first.toString())
                .header("Idempotency-Key", newKey()).header("Authorization", actor.bearer())).andExpect(status().isCreated());

        mockMvc.perform(get(listUrl(claim)).header("Authorization", actor.bearer()))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.items[?(@.id == '" + first + "')].superseded").value(true));
    }

    @Test
    void aListNeverIncludesAnotherClaimsOrOrganizationsDocuments() throws Exception {
        TestActors actor = actor();
        TestActors other = actor();
        UUID claim = claimFor(actor);
        uploadOk(actor, claim, pdf(TestFiles.pdf(1)), "OTHER");
        uploadOk(actor, claimFor(actor), pdf(TestFiles.pdf(2)), "OTHER");
        uploadOk(other, claimFor(other), pdf(TestFiles.pdf(3)), "OTHER");

        mockMvc.perform(get(listUrl(claim)).header("Authorization", actor.bearer()))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get(listUrl(claim)).header("Authorization", other.bearer()))
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- single document

    @Test
    void returnsOneDocumentsMetadata() throws Exception {
        TestActors actor = actor();
        UUID claim = claimFor(actor);
        UUID id = uploadOk(actor, claim, pdf(TestFiles.pdf(1)), "SURVEYOR_REPORT");

        mockMvc.perform(get(listUrl(claim) + "/" + id).header("Authorization", actor.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.documentType").value("SURVEYOR_REPORT"))
                .andExpect(jsonPath("$.status").value("RECEIVED"));
    }

    @Test
    void aDocumentIsFoundOnlyThroughItsOwnClaimAndOrganization() throws Exception {
        TestActors actor = actor();
        TestActors other = actor();
        UUID claim = claimFor(actor);
        UUID otherClaim = claimFor(actor);
        UUID id = uploadOk(actor, claim, pdf(TestFiles.pdf(1)), "OTHER");

        mockMvc.perform(get(listUrl(otherClaim) + "/" + id).header("Authorization", actor.bearer())).andExpect(status().isNotFound());
        mockMvc.perform(get(listUrl(claim) + "/" + id).header("Authorization", other.bearer())).andExpect(status().isNotFound());
        mockMvc.perform(get(listUrl(claim) + "/" + UUID.randomUUID()).header("Authorization", actor.bearer())).andExpect(status().isNotFound());
        mockMvc.perform(get(listUrl(claim) + "/not-a-uuid").header("Authorization", actor.bearer())).andExpect(status().isBadRequest());
    }

    @Test
    void readingRequiresALogin() throws Exception {
        TestActors actor = actor();
        UUID claim = claimFor(actor);
        UUID id = uploadOk(actor, claim, pdf(TestFiles.pdf(1)), "OTHER");

        for (String url : new String[]{listUrl(claim), listUrl(claim) + "/" + id, listUrl(claim) + "/" + id + "/content"}) {
            mockMvc.perform(get(url)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
            mockMvc.perform(get(url).header("Authorization", "Bearer " + TestTokens.expired(jwtSecret, actor.email())))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"));
        }
    }

    // ---------------------------------------------------------------- download

    @Test
    void streamsTheOriginalBytesWithSafeHeaders() throws Exception {
        TestActors actor = actor();
        UUID claim = claimFor(actor);
        byte[] original = TestFiles.pdfOfSize(5, 50_000);
        UUID id = uploadOk(actor, claim, file("estimate-final.pdf", "application/pdf", original), "GARAGE_ESTIMATE");

        MvcResult result = mockMvc.perform(get(listUrl(claim) + "/" + id + "/content").header("Authorization", actor.bearer()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().longValue("Content-Length", original.length))
                .andExpect(header().string("Content-Disposition", allOf(startsWith("attachment"), containsString("estimate-final.pdf"))))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", allOf(containsString("no-store"), containsString("private"))))
                .andExpect(header().string("Content-Security-Policy", containsString("sandbox")))
                .andReturn();

        assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(original);
    }

    @Test
    void theDownloadNeverPointsAtStorage() throws Exception {
        TestActors actor = actor();
        UUID claim = claimFor(actor);
        UUID id = uploadOk(actor, claim, pdf(TestFiles.pdf(1)), "OTHER");

        MvcResult result = mockMvc.perform(get(listUrl(claim) + "/" + id + "/content").header("Authorization", actor.bearer())).andReturn();

        assertThat(result.getResponse().getHeaderNames()).doesNotContain("Location", "Link", "Refresh");
        assertThat(result.getResponse().getHeaders("Content-Disposition").toString()).doesNotContain(objectKeyOf(id));
    }

    @Test
    void aNonLatinFileNameIsEncodedInTheDownloadHeader() throws Exception {
        TestActors actor = actor();
        UUID claim = claimFor(actor);
        UUID id = uploadOk(actor, claim, file("रसीद.pdf", "application/pdf", TestFiles.pdf(1)), "OTHER");

        mockMvc.perform(get(listUrl(claim) + "/" + id + "/content").header("Authorization", actor.bearer()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString(URLEncoder.encode("रसीद", StandardCharsets.UTF_8).replace("+", "%20"))));
    }

    @Test
    void imagesAreServedWithTheirRealType() throws Exception {
        TestActors actor = actor();
        UUID claim = claimFor(actor);
        UUID id = uploadOk(actor, claim, file("damage.bin", "application/octet-stream", TestFiles.jpeg(1)), "DAMAGE_PHOTOGRAPH");

        mockMvc.perform(get(listUrl(claim) + "/" + id + "/content").header("Authorization", actor.bearer()))
                .andExpect(header().string("Content-Type", "image/jpeg"));
    }

    @Test
    void anotherOrganizationCannotDownload() throws Exception {
        TestActors actor = actor();
        TestActors other = actor();
        UUID claim = claimFor(actor);
        UUID id = uploadOk(actor, claim, pdf(TestFiles.pdf(1)), "OTHER");

        mockMvc.perform(get(listUrl(claim) + "/" + id + "/content").header("Authorization", other.bearer()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/json"));
    }

    @Test
    void bothRolesMayDownload() throws Exception {
        TestActors admin = actor("ORG_ADMIN");
        UUID claim = claimFor(admin);
        UUID id = uploadOk(admin, claim, pdf(TestFiles.pdf(1)), "OTHER");
        TestActors investigator = TestActors.inOrganization(fixtures, jwtSecret, admin.organizationId(), "INVESTIGATOR");

        for (TestActors reader : new TestActors[]{admin, investigator}) {
            mockMvc.perform(get(listUrl(claim) + "/" + id + "/content").header("Authorization", reader.bearer())).andExpect(status().isOk());
        }
    }

    @Test
    void aMissingStoredObjectIsAServerErrorThatLeaksNothing() throws Exception {
        TestActors actor = actor();
        UUID claim = claimFor(actor);
        UUID id = uploadOk(actor, claim, pdf(TestFiles.pdf(1)), "OTHER");
        String key = objectKeyOf(id);
        storage.delete(key);

        mockMvc.perform(get(listUrl(claim) + "/" + id + "/content").header("Authorization", actor.bearer()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(content().string(not(containsString(key))));
    }
}
