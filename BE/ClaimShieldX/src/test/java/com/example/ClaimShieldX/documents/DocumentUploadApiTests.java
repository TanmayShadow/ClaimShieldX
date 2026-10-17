package com.example.ClaimShieldX.documents;

import com.example.ClaimShieldX.support.TestActors;
import com.example.ClaimShieldX.support.TestFiles;
import com.example.ClaimShieldX.support.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DocumentUploadApiTests extends DocumentApiTestBase {
    private static String sha256(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }

    // ---------------------------------------------------------------- accepted uploads

    @Test
    void storesAPdfAndReturnsItsMetadata() throws Exception {
        TestActors actor = actor();
        UUID claim = claimFor(actor);
        byte[] content = TestFiles.pdf(1);

        upload(actor, claim, pdf(content), "FINAL_INVOICE")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/v1/claims/" + claim + "/documents/")))
                .andExpect(header().doesNotExist("Idempotent-Replay"))
                .andExpect(jsonPath("$.id", not(emptyOrNullString())))
                .andExpect(jsonPath("$.claimId").value(claim.toString()))
                .andExpect(jsonPath("$.documentType").value("FINAL_INVOICE"))
                .andExpect(jsonPath("$.fileName").value("invoice.pdf"))
                .andExpect(jsonPath("$.contentType").value("application/pdf"))
                .andExpect(jsonPath("$.sizeBytes").value(content.length))
                .andExpect(jsonPath("$.sha256").value(sha256(content)))
                .andExpect(jsonPath("$.status").value("RECEIVED"))
                .andExpect(jsonPath("$.superseded").value(false))
                .andExpect(jsonPath("$.supersedesDocumentId").doesNotExist())
                .andExpect(jsonPath("$.receivedAt", not(emptyOrNullString())));
    }

    @Test
    void theResponseNeverExposesStorageDetailsOrInternalIds() throws Exception {
        TestActors actor = actor();

        upload(actor, claimFor(actor), pdf(TestFiles.pdf(2)), "FINAL_INVOICE")
                .andExpect(jsonPath("$.objectKey").doesNotExist())
                .andExpect(jsonPath("$.storageUrl").doesNotExist())
                .andExpect(jsonPath("$.organizationId").doesNotExist())
                .andExpect(jsonPath("$.uploadedBy").doesNotExist());
    }

    @Test
    void persistsTheRowAndTheExactBytes() throws Exception {
        TestActors actor = actor();
        UUID claim = claimFor(actor);
        byte[] content = TestFiles.pdf(3);

        UUID id = uploadOk(actor, claim, pdf(content), "GARAGE_ESTIMATE");

        Map<String, Object> row = jdbc.queryForMap("select * from documents where id = ?", id);
        assertThat(row.get("organization_id")).isEqualTo(actor.organizationId());
        assertThat(row.get("claim_id")).isEqualTo(claim);
        assertThat(row.get("uploaded_by")).isEqualTo(actor.userId());
        assertThat(row.get("document_type")).isEqualTo("GARAGE_ESTIMATE");
        assertThat(row.get("status")).isEqualTo("RECEIVED");
        assertThat(row.get("sha256")).isEqualTo(sha256(content));
        assertThat(row.get("size_bytes")).isEqualTo((long) content.length);
        try (InputStream stored = storage.open((String) row.get("object_key"))) {
            assertThat(stored.readAllBytes()).isEqualTo(content);
        }
    }

    @Test
    void theStorageKeyIsRandomAndNeverContainsTheClientFileName() throws Exception {
        TestActors actor = actor();
        UUID claim = claimFor(actor);

        UUID id = uploadOk(actor, claim, file("RAHUL-SHARMA-secret-invoice.pdf", "application/pdf", TestFiles.pdf(4)), "FINAL_INVOICE");

        String key = objectKeyOf(id);
        assertThat(key).matches("documents/" + actor.organizationId() + "/" + claim + "/[0-9a-f-]{36}");
        assertThat(key.toLowerCase()).doesNotContain("rahul").doesNotContain("secret").doesNotContain("invoice").doesNotContain(".pdf");
    }

    @Test
    void jpegAndPngAreAcceptedWhateverTheBrowserDeclares() throws Exception {
        TestActors actor = actor();
        UUID claim = claimFor(actor);

        upload(actor, claim, file("photo.jpg", "application/octet-stream", TestFiles.jpeg(1)), "DAMAGE_PHOTOGRAPH")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.contentType").value("image/jpeg"));
        upload(actor, claim, file("photo.png", "text/plain", TestFiles.png(2)), "DAMAGE_PHOTOGRAPH")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.contentType").value("image/png"));
    }

    @Test
    void theRealTypeWinsOverTheFileNameAndDeclaredType() throws Exception {
        TestActors actor = actor();

        upload(actor, claimFor(actor), file("scan.pdf", "application/pdf", TestFiles.png(1)), "FINAL_INVOICE")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentType").value("image/png"));
    }

    @Test
    void theFileNameIsSanitizedForDisplay() throws Exception {
        TestActors actor = actor();

        upload(actor, claimFor(actor), file("../../etc/passwd.pdf", "application/pdf", TestFiles.pdf(5)), "FINAL_INVOICE")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value("passwd.pdf"));
        upload(actor, claimFor(actor), file("", "application/pdf", TestFiles.pdf(6)), "FINAL_INVOICE")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value("document.pdf"));
    }

    @Test
    void anExternalDocumentIdIsStoredTrimmed() throws Exception {
        TestActors actor = actor();
        UUID claim = claimFor(actor);

        mockMvc.perform(multipart(HttpMethod.POST, "/api/v1/claims/{claimId}/documents", claim)
                        .file(pdf(TestFiles.pdf(7))).param("documentType", "FINAL_INVOICE").param("externalDocumentId", "  DOC-DEMO-01 ")
                        .header("Idempotency-Key", newKey()).header("Authorization", actor.bearer()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.externalDocumentId").value("DOC-DEMO-01"));
    }

    @Test
    void bothRolesMayUpload() throws Exception {
        for (String role : new String[]{"INVESTIGATOR", "ORG_ADMIN"}) {
            TestActors actor = actor(role);
            upload(actor, claimFor(actor), pdf(TestFiles.pdf(8)), "CLAIM_FORM").andExpect(status().isCreated());
        }
    }

    // ---------------------------------------------------------------- rejected uploads

    @Test
    void aProgramDisguisedAsAPdfIsRejectedAndNothingIsStored() throws Exception {
        TestActors actor = actor();
        UUID claim = claimFor(actor);

        upload(actor, claim, file("invoice.pdf", "application/pdf", TestFiles.windowsExecutable()), "FINAL_INVOICE")
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));

        assertThat(documentCount(claim)).isZero();
    }

    @Test
    void textArchivesAndTinyFilesAreRejected() throws Exception {
        TestActors actor = actor();
        UUID claim = claimFor(actor);

        for (byte[] bad : new byte[][]{TestFiles.plainText(), TestFiles.zipArchive(), {1, 2, 3}}) {
            upload(actor, claim, file("x.pdf", "application/pdf", bad), "FINAL_INVOICE")
                    .andExpect(status().isUnsupportedMediaType());
        }
        assertThat(documentCount(claim)).isZero();
    }

    @Test
    void anEmptyFileIsRejected() throws Exception {
        TestActors actor = actor();

        upload(actor, claimFor(actor), file("empty.pdf", "application/pdf", new byte[0]), "FINAL_INVOICE")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMPTY_FILE"));
    }

    @Test
    void aMissingFilePartIsAValidationError() throws Exception {
        TestActors actor = actor();

        mockMvc.perform(multipart(HttpMethod.POST, "/api/v1/claims/{claimId}/documents", claimFor(actor))
                        .param("documentType", "FINAL_INVOICE").header("Idempotency-Key", newKey()).header("Authorization", actor.bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("file")));
    }

    @Test
    void aMissingOrUnknownDocumentTypeIsAValidationErrorThatDoesNotEchoTheValue() throws Exception {
        TestActors actor = actor();
        UUID claim = claimFor(actor);

        mockMvc.perform(multipart(HttpMethod.POST, "/api/v1/claims/{claimId}/documents", claim)
                        .file(pdf(TestFiles.pdf(1))).header("Idempotency-Key", newKey()).header("Authorization", actor.bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("documentType")));

        upload(actor, claim, pdf(TestFiles.pdf(1)), "NOT_A_REAL_TYPE_zq91")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("documentType")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(not(containsString("zq91"))));
    }

    @Test
    void theIdempotencyKeyIsRequiredAndMustBeWellFormed() throws Exception {
        TestActors actor = actor();
        UUID claim = claimFor(actor);

        mockMvc.perform(multipart(HttpMethod.POST, "/api/v1/claims/{claimId}/documents", claim)
                        .file(pdf(TestFiles.pdf(1))).param("documentType", "FINAL_INVOICE").header("Authorization", actor.bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));

        for (String bad : new String[]{"short", "has spaces in it", "bad/slash/12345", "x".repeat(129)}) {
            upload(actor, claim, pdf(TestFiles.pdf(1)), "FINAL_INVOICE", bad)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors[*].field", hasItem("Idempotency-Key")));
        }
        assertThat(documentCount(claim)).isZero();
    }

    @Test
    void anOverlongExternalDocumentIdIsRejected() throws Exception {
        TestActors actor = actor();

        mockMvc.perform(multipart(HttpMethod.POST, "/api/v1/claims/{claimId}/documents", claimFor(actor))
                        .file(pdf(TestFiles.pdf(1))).param("documentType", "FINAL_INVOICE").param("externalDocumentId", "x".repeat(256))
                        .header("Idempotency-Key", newKey()).header("Authorization", actor.bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("externalDocumentId")));
    }

    @Test
    void aNonMultipartRequestIsAnUnsupportedMediaType() throws Exception {
        TestActors actor = actor();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/claims/{claimId}/documents", claimFor(actor))
                        .contentType("application/json").content("{}").header("Idempotency-Key", newKey()).header("Authorization", actor.bearer()))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    // ---------------------------------------------------------------- authentication and tenancy

    @Test
    void uploadingWithoutATokenOrWithAnExpiredOneIsUnauthorized() throws Exception {
        TestActors actor = actor();
        UUID claim = claimFor(actor);

        mockMvc.perform(multipart(HttpMethod.POST, "/api/v1/claims/{claimId}/documents", claim)
                        .file(pdf(TestFiles.pdf(1))).param("documentType", "FINAL_INVOICE").header("Idempotency-Key", newKey()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        mockMvc.perform(multipart(HttpMethod.POST, "/api/v1/claims/{claimId}/documents", claim)
                        .file(pdf(TestFiles.pdf(1))).param("documentType", "FINAL_INVOICE").header("Idempotency-Key", newKey())
                        .header("Authorization", "Bearer " + TestTokens.expired(jwtSecret, actor.email())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"));
        assertThat(documentCount(claim)).isZero();
    }

    @Test
    void anotherOrganizationsClaimLooksExactlyLikeAMissingOne() throws Exception {
        TestActors owner = actor();
        TestActors intruder = actor();
        UUID ownersClaim = claimFor(owner);

        upload(intruder, ownersClaim, pdf(TestFiles.pdf(1)), "FINAL_INVOICE")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        upload(intruder, UUID.randomUUID(), pdf(TestFiles.pdf(1)), "FINAL_INVOICE")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        assertThat(documentCount(ownersClaim)).isZero();
    }

    @Test
    void aMalformedClaimIdIsABadRequest() throws Exception {
        TestActors actor = actor();

        mockMvc.perform(multipart(HttpMethod.POST, "/api/v1/claims/{claimId}/documents", "not-a-uuid")
                        .file(pdf(TestFiles.pdf(1))).param("documentType", "FINAL_INVOICE").header("Idempotency-Key", newKey())
                        .header("Authorization", actor.bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("claimId")));
    }

    // ---------------------------------------------------------------- duplicates

    @Test
    void theSameFileTwiceForTheSameClaimAndTypeIsAConflictThatNamesTheExistingDocument() throws Exception {
        TestActors actor = actor();
        UUID claim = claimFor(actor);
        byte[] content = TestFiles.pdf(9);
        UUID first = uploadOk(actor, claim, pdf(content), "FINAL_INVOICE");

        upload(actor, claim, pdf(content), "FINAL_INVOICE")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_DOCUMENT"))
                .andExpect(jsonPath("$.details.existingDocumentId").value(first.toString()));

        assertThat(documentCount(claim)).isEqualTo(1);
    }

    @Test
    void theSameFileIsFineForAnotherTypeOrAnotherClaim() throws Exception {
        TestActors actor = actor();
        UUID claim = claimFor(actor);
        UUID otherClaim = claimFor(actor);
        byte[] content = TestFiles.pdf(10);
        uploadOk(actor, claim, pdf(content), "FINAL_INVOICE");

        upload(actor, claim, pdf(content), "GARAGE_ESTIMATE").andExpect(status().isCreated());
        upload(actor, otherClaim, pdf(content), "FINAL_INVOICE").andExpect(status().isCreated());
    }

    // ---------------------------------------------------------------- revisions

    @Test
    void aRevisionRetiresTheOriginalButKeepsIt() throws Exception {
        TestActors actor = actor();
        UUID claim = claimFor(actor);
        byte[] original = TestFiles.pdf(11);
        UUID first = uploadOk(actor, claim, pdf(original), "FINAL_INVOICE");

        mockMvc.perform(multipart(HttpMethod.POST, "/api/v1/claims/{claimId}/documents", claim)
                        .file(pdf(TestFiles.pdf(12))).param("documentType", "FINAL_INVOICE").param("supersedesDocumentId", first.toString())
                        .header("Idempotency-Key", newKey()).header("Authorization", actor.bearer()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.supersedesDocumentId").value(first.toString()))
                .andExpect(jsonPath("$.superseded").value(false));

        assertThat(jdbc.queryForObject("select superseded from documents where id = ?", Boolean.class, first)).isTrue();
        assertThat(documentCount(claim)).isEqualTo(2);
        try (InputStream stored = storage.open(objectKeyOf(first))) {
            assertThat(stored.readAllBytes()).as("the superseded original is kept").isEqualTo(original);
        }
    }

    @Test
    void aSupersededFileMayBeUploadedAgain() throws Exception {
        TestActors actor = actor();
        UUID claim = claimFor(actor);
        byte[] original = TestFiles.pdf(13);
        UUID first = uploadOk(actor, claim, pdf(original), "FINAL_INVOICE");
        mockMvc.perform(multipart(HttpMethod.POST, "/api/v1/claims/{claimId}/documents", claim)
                .file(pdf(TestFiles.pdf(14))).param("documentType", "FINAL_INVOICE").param("supersedesDocumentId", first.toString())
                .header("Idempotency-Key", newKey()).header("Authorization", actor.bearer())).andExpect(status().isCreated());

        upload(actor, claim, pdf(original), "FINAL_INVOICE").andExpect(status().isCreated());
    }

    @Test
    void aRevisionMustReferenceAKnownDocumentOfTheSameClaimAndType() throws Exception {
        TestActors actor = actor();
        TestActors other = actor();
        UUID claim = claimFor(actor);
        UUID otherClaim = claimFor(actor);
        UUID target = uploadOk(actor, claim, pdf(TestFiles.pdf(15)), "FINAL_INVOICE");
        UUID inOtherClaim = uploadOk(actor, otherClaim, pdf(TestFiles.pdf(16)), "FINAL_INVOICE");
        UUID foreign = uploadOk(other, claimFor(other), pdf(TestFiles.pdf(17)), "FINAL_INVOICE");

        for (UUID bad : new UUID[]{UUID.randomUUID(), inOtherClaim, foreign}) {
            mockMvc.perform(multipart(HttpMethod.POST, "/api/v1/claims/{claimId}/documents", claim)
                            .file(pdf(TestFiles.pdf(18))).param("documentType", "FINAL_INVOICE").param("supersedesDocumentId", bad.toString())
                            .header("Idempotency-Key", newKey()).header("Authorization", actor.bearer()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("supersedesDocumentId"))
                    .andExpect(jsonPath("$.errors[0].message").value("document not found"));
        }
        mockMvc.perform(multipart(HttpMethod.POST, "/api/v1/claims/{claimId}/documents", claim)
                        .file(pdf(TestFiles.pdf(19))).param("documentType", "GARAGE_ESTIMATE").param("supersedesDocumentId", target.toString())
                        .header("Idempotency-Key", newKey()).header("Authorization", actor.bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value("must have the same document type"));
        assertThat(documentCount(claim)).isEqualTo(1);
    }

    @Test
    void aDocumentCanBeReplacedOnlyOnce() throws Exception {
        TestActors actor = actor();
        UUID claim = claimFor(actor);
        UUID first = uploadOk(actor, claim, pdf(TestFiles.pdf(20)), "FINAL_INVOICE");
        mockMvc.perform(multipart(HttpMethod.POST, "/api/v1/claims/{claimId}/documents", claim)
                .file(pdf(TestFiles.pdf(21))).param("documentType", "FINAL_INVOICE").param("supersedesDocumentId", first.toString())
                .header("Idempotency-Key", newKey()).header("Authorization", actor.bearer())).andExpect(status().isCreated());

        mockMvc.perform(multipart(HttpMethod.POST, "/api/v1/claims/{claimId}/documents", claim)
                        .file(pdf(TestFiles.pdf(22))).param("documentType", "FINAL_INVOICE").param("supersedesDocumentId", first.toString())
                        .header("Idempotency-Key", newKey()).header("Authorization", actor.bearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
        assertThat(documentCount(claim)).isEqualTo(2);
    }

    @Test
    void aRevisionIdenticalToTheFileItReplacesIsADuplicate() throws Exception {
        TestActors actor = actor();
        UUID claim = claimFor(actor);
        byte[] content = TestFiles.pdf(23);
        UUID first = uploadOk(actor, claim, pdf(content), "FINAL_INVOICE");

        mockMvc.perform(multipart(HttpMethod.POST, "/api/v1/claims/{claimId}/documents", claim)
                        .file(pdf(content)).param("documentType", "FINAL_INVOICE").param("supersedesDocumentId", first.toString())
                        .header("Idempotency-Key", newKey()).header("Authorization", actor.bearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_DOCUMENT"));
    }
}
