package com.example.ClaimShieldX.documents;

import com.example.ClaimShieldX.idempotency.service.IdempotencyService;
import com.example.ClaimShieldX.support.TestActors;
import com.example.ClaimShieldX.support.TestFiles;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MvcResult;

import java.io.File;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DocumentIdempotencyApiTests extends DocumentApiTestBase {
    private static String sha256(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }

    private int keyRecords(UUID organization, String key) {
        return jdbc.queryForObject("select count(*) from idempotency_keys where organization_id = ? and idempotency_key = ?", Integer.class, organization, key);
    }

    private int storedFiles(UUID organization, UUID claim) {
        File folder = new File(storagePath, "documents/" + organization + "/" + claim);
        File[] files = folder.listFiles();
        return files == null ? 0 : files.length;
    }

    @Test
    void repeatingTheSameRequestReturnsTheSameDocumentAndCreatesNothingNew() throws Exception {
        TestActors actor = actor();
        UUID claim = claimFor(actor);
        byte[] content = TestFiles.pdf(1);
        String key = newKey();

        MvcResult first = upload(actor, claim, pdf(content), "FINAL_INVOICE", key)
                .andExpect(status().isCreated()).andExpect(header().doesNotExist("Idempotent-Replay")).andReturn();
        String id = JsonPath.read(first.getResponse().getContentAsString(), "$.id");

        upload(actor, claim, pdf(content), "FINAL_INVOICE", key)
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replay", "true"))
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.sha256").value(sha256(content)));

        assertThat(documentCount(claim)).isEqualTo(1);
        assertThat(storedFiles(actor.organizationId(), claim)).isEqualTo(1);
    }

    @Test
    void aReplayStillFollowsTheLaterStateOfTheDocument() throws Exception {
        TestActors actor = actor();
        UUID claim = claimFor(actor);
        byte[] content = TestFiles.pdf(2);
        String key = newKey();
        UUID id = UUID.fromString(JsonPath.read(upload(actor, claim, pdf(content), "FINAL_INVOICE", key).andReturn().getResponse().getContentAsString(), "$.id"));
        jdbc.update("update documents set status = 'VERIFIED' where id = ?", id);

        upload(actor, claim, pdf(content), "FINAL_INVOICE", key)
                .andExpect(header().string("Idempotent-Replay", "true"))
                .andExpect(jsonPath("$.status").value("VERIFIED"));
    }

    @Test
    void theSameKeyWithADifferentRequestIsRejected() throws Exception {
        TestActors actor = actor();
        UUID claim = claimFor(actor);
        UUID otherClaim = claimFor(actor);
        String key = newKey();
        upload(actor, claim, pdf(TestFiles.pdf(3)), "FINAL_INVOICE", key).andExpect(status().isCreated());

        upload(actor, claim, pdf(TestFiles.pdf(4)), "FINAL_INVOICE", key)
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        upload(actor, claim, pdf(TestFiles.pdf(3)), "GARAGE_ESTIMATE", key)
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        upload(actor, otherClaim, pdf(TestFiles.pdf(3)), "FINAL_INVOICE", key)
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));

        assertThat(documentCount(claim)).isEqualTo(1);
        assertThat(documentCount(otherClaim)).isZero();
    }

    @Test
    void keysAreScopedPerOrganization() throws Exception {
        TestActors first = actor();
        TestActors second = actor();
        String sharedKey = newKey();

        upload(first, claimFor(first), pdf(TestFiles.pdf(5)), "FINAL_INVOICE", sharedKey).andExpect(status().isCreated()).andExpect(header().doesNotExist("Idempotent-Replay"));
        upload(second, claimFor(second), pdf(TestFiles.pdf(6)), "FINAL_INVOICE", sharedKey).andExpect(status().isCreated()).andExpect(header().doesNotExist("Idempotent-Replay"));
    }

    @Test
    void aFailedRequestDoesNotUseUpItsKey() throws Exception {
        TestActors actor = actor();
        UUID claim = claimFor(actor);
        String key = newKey();

        upload(actor, claim, file("bad.pdf", "application/pdf", TestFiles.windowsExecutable()), "FINAL_INVOICE", key)
                .andExpect(status().isUnsupportedMediaType());
        assertThat(keyRecords(actor.organizationId(), key)).isZero();

        upload(actor, claim, pdf(TestFiles.pdf(7)), "FINAL_INVOICE", key).andExpect(status().isCreated());
    }

    @Test
    void aBusinessFailureAfterTheKeyWasTakenReleasesIt() throws Exception {
        TestActors actor = actor();
        UUID claim = claimFor(actor);
        byte[] content = TestFiles.pdf(8);
        uploadOk(actor, claim, pdf(content), "FINAL_INVOICE");
        String key = newKey();

        upload(actor, claim, pdf(content), "FINAL_INVOICE", key).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_DOCUMENT"));

        assertThat(keyRecords(actor.organizationId(), key)).as("the 409 must not leave the key stuck in progress").isZero();
    }

    @Test
    void aRequestStillInProgressIsReportedNotRunTwice() throws Exception {
        TestActors actor = actor();
        UUID claim = claimFor(actor);
        byte[] content = TestFiles.pdf(9);
        String key = newKey();
        String fingerprint = IdempotencyService.fingerprint(claim.toString(), "FINAL_INVOICE", "", "", sha256(content));
        jdbc.update("""
                insert into idempotency_keys(id, organization_id, operation, idempotency_key, request_fingerprint, state, expires_at)
                values (?, ?, 'POST /claims/{claimId}/documents', ?, ?, 'IN_PROGRESS', now() + interval '1 hour')
                """, UUID.randomUUID(), actor.organizationId(), key, fingerprint);

        upload(actor, claim, pdf(content), "FINAL_INVOICE", key)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REQUEST_IN_PROGRESS"));

        assertThat(documentCount(claim)).isZero();
    }

    @Test
    void aRequestThatDiedMidWayDoesNotBlockTheClientForever() throws Exception {
        TestActors actor = actor();
        UUID claim = claimFor(actor);
        byte[] content = TestFiles.pdf(10);
        String key = newKey();
        String fingerprint = IdempotencyService.fingerprint(claim.toString(), "FINAL_INVOICE", "", "", sha256(content));
        jdbc.update("""
                insert into idempotency_keys(id, organization_id, operation, idempotency_key, request_fingerprint, state, created_at, expires_at)
                values (?, ?, 'POST /claims/{claimId}/documents', ?, ?, 'IN_PROGRESS', now() - interval '10 minutes', now() + interval '1 hour')
                """, UUID.randomUUID(), actor.organizationId(), key, fingerprint);

        upload(actor, claim, pdf(content), "FINAL_INVOICE", key).andExpect(status().isCreated());

        assertThat(documentCount(claim)).isEqualTo(1);
    }

    @Test
    void anExpiredKeyIsForgottenSoItCanBeReusedForADifferentRequest() throws Exception {
        TestActors actor = actor();
        UUID claim = claimFor(actor);
        String key = newKey();
        upload(actor, claim, pdf(TestFiles.pdf(11)), "FINAL_INVOICE", key).andExpect(status().isCreated());
        jdbc.update("update idempotency_keys set expires_at = now() - interval '1 minute' where organization_id = ? and idempotency_key = ?", actor.organizationId(), key);

        upload(actor, claim, pdf(TestFiles.pdf(12)), "FINAL_INVOICE", key)
                .andExpect(status().isCreated()).andExpect(header().doesNotExist("Idempotent-Replay"));
    }

    @Test
    void theHeaderNameIsCaseInsensitive() throws Exception {
        TestActors actor = actor();

        mockMvc.perform(multipart(HttpMethod.POST, "/api/v1/claims/{claimId}/documents", claimFor(actor))
                        .file(pdf(TestFiles.pdf(13))).param("documentType", "FINAL_INVOICE")
                        .header("idempotency-key", newKey()).header("Authorization", actor.bearer()))
                .andExpect(status().isCreated());
    }

    @Test
    void simultaneousRequestsWithTheSameKeyStoreExactlyOneDocument() throws Exception {
        int parallel = 6;
        TestActors actor = actor();
        UUID claim = claimFor(actor);
        byte[] content = TestFiles.pdf(14);
        String key = newKey();
        ExecutorService pool = Executors.newFixedThreadPool(parallel);
        try {
            CountDownLatch ready = new CountDownLatch(parallel);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<MvcResult>> outcomes = new ArrayList<>();
            for (int i = 0; i < parallel; i++) {
                outcomes.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return upload(actor, claim, pdf(content), "FINAL_INVOICE", key).andReturn();
                }));
            }
            ready.await();
            go.countDown();

            int created = 0;
            int replays = 0;
            int inProgress = 0;
            for (Future<MvcResult> outcome : outcomes) {
                MvcResult result = outcome.get(60, TimeUnit.SECONDS);
                int code = result.getResponse().getStatus();
                if (code == 201 && "true".equals(result.getResponse().getHeader("Idempotent-Replay"))) {
                    replays++;
                } else if (code == 201) {
                    created++;
                } else {
                    assertThat(code).as("only REQUEST_IN_PROGRESS is an acceptable failure; body: " + result.getResponse().getContentAsString()).isEqualTo(409);
                    assertThat(result.getResponse().getContentAsString()).contains("REQUEST_IN_PROGRESS");
                    inProgress++;
                }
            }

            assertThat(created).as("exactly one request does the work").isEqualTo(1);
            assertThat(created + replays + inProgress).isEqualTo(parallel);
            assertThat(documentCount(claim)).isEqualTo(1);
            assertThat(storedFiles(actor.organizationId(), claim)).as("and exactly one file is stored, with no orphans").isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }
}
