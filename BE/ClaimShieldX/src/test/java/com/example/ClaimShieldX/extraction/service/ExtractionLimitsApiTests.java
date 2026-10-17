package com.example.ClaimShieldX.extraction.service;

import com.example.ClaimShieldX.extraction.support.TestPdfs;
import com.example.ClaimShieldX.support.TestActors;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static com.example.ClaimShieldX.extraction.support.TestDocuments.invoice;
import static com.example.ClaimShieldX.extraction.support.TestDocuments.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The size, page, attempt and timeout limits, with small limits so the cases are cheap to build. */
@TestPropertySource(properties = {
        "app.extraction.max-inline-bytes=30000",
        "app.extraction.max-pages=2",
        "app.extraction.max-attempts-per-document=2",
        "app.extraction.processing-timeout=2s"})
class ExtractionLimitsApiTests extends ExtractionApiTestBase {
    private final String valid = json(invoice(GARAGE, VEHICLE, "12/09/2026"));

    @Test
    void aFileOverTheInlineLimitIsNeverSentToTheModel() throws Exception {
        TestActors actor = actor();
        UUID claim = claimWithKnownFacts(actor);
        byte[] big = com.example.ClaimShieldX.support.TestFiles.pdfOfSize(1, 40_000);
        UUID document = uploadOk(actor, claim, pdf(big), "FINAL_INVOICE");

        startExtraction(actor, claim, document).andExpect(status().isAccepted()).andExpect(jsonPath("$.documentStatus").value("FAILED"));

        readExtraction(actor, claim, document).andExpect(jsonPath("$.failureCode").value("DOCUMENT_TOO_LARGE")).andExpect(jsonPath("$.retryable").value(false));
        assertThat(model.callCount()).isZero();
    }

    @Test
    void aFileExactlyAtTheLimitIsStillSent() throws Exception {
        TestActors actor = actor();
        UUID claim = claimWithKnownFacts(actor);
        byte[] pdf = TestPdfs.pdf(1, 5);
        // Pad the PDF (comments after %%EOF are ignored by readers) up to the limit exactly.
        byte[] padded = java.util.Arrays.copyOf(pdf, 30_000);
        java.util.Arrays.fill(padded, pdf.length, padded.length, (byte) '\n');
        UUID document = uploadOk(actor, claim, pdf(padded), "FINAL_INVOICE");
        model.thenReturn(valid);

        startExtraction(actor, claim, document).andExpect(jsonPath("$.documentStatus").value("VERIFIED"));

        assertThat(model.requests().get(0).content()).hasSize(30_000);
    }

    @Test
    void aPdfWithMoreThanTheAllowedPagesIsRefused() throws Exception {
        TestActors actor = actor();
        UUID claim = claimWithKnownFacts(actor);
        UUID document = uploadOk(actor, claim, pdf(TestPdfs.pdf(3, 6)), "FINAL_INVOICE");

        startExtraction(actor, claim, document).andExpect(jsonPath("$.documentStatus").value("FAILED"));

        readExtraction(actor, claim, document).andExpect(jsonPath("$.failureCode").value("UNREADABLE_FILE")).andExpect(jsonPath("$.pageCount").value(3));
        assertThat(model.callCount()).isZero();
    }

    @Test
    void aFileThatLooksLikeAPdfButCannotBeOpenedIsRefusedWithoutCallingTheModel() throws Exception {
        TestActors actor = actor();
        UUID claim = claimWithKnownFacts(actor);
        UUID document = uploadOk(actor, claim, pdf(com.example.ClaimShieldX.support.TestFiles.pdf(31)), "FINAL_INVOICE");

        startExtraction(actor, claim, document).andExpect(jsonPath("$.documentStatus").value("FAILED"));

        readExtraction(actor, claim, document).andExpect(jsonPath("$.failureCode").value("UNREADABLE_FILE")).andExpect(jsonPath("$.retryable").value(false));
        assertThat(model.callCount()).isZero();
    }

    @Test
    void theAttemptLimitComesFromConfiguration() throws Exception {
        TestActors actor = actor();
        UUID claim = claimWithKnownFacts(actor);
        UUID document = uploadOk(actor, claim, pdf(TestPdfs.pdf(1, 7)), "FINAL_INVOICE");
        model.thenFail(new com.example.ClaimShieldX.extraction.client.ModelCallException(
                com.example.ClaimShieldX.extraction.client.ModelCallException.Category.REJECTED_INPUT, "x", null))
                .thenFail(new com.example.ClaimShieldX.extraction.client.ModelCallException(
                        com.example.ClaimShieldX.extraction.client.ModelCallException.Category.REJECTED_INPUT, "x", null));

        startExtraction(actor, claim, document).andExpect(status().isAccepted());
        startExtraction(actor, claim, document).andExpect(status().isAccepted());
        startExtraction(actor, claim, document).andExpect(status().isConflict());

        assertThat(extractionRows(document)).isEqualTo(2);
    }

    @Test
    void aDocumentStuckInProcessingBecomesExtractableAgainAfterTheTimeout() throws Exception {
        TestActors actor = actor();
        UUID claim = claimWithKnownFacts(actor);
        UUID document = uploadOk(actor, claim, pdf(TestPdfs.pdf(1, 8)), "FINAL_INVOICE");
        // A run that died: PROCESSING since well before the 2-second timeout.
        jdbc.update("update documents set status = 'PROCESSING', updated_at = ? where id = ?", Timestamp.from(Instant.now().minusSeconds(60)), document);
        model.thenReturn(valid);

        startExtraction(actor, claim, document).andExpect(status().isAccepted()).andExpect(jsonPath("$.documentStatus").value("VERIFIED"));

        assertThat(documentStatus(document)).isEqualTo("VERIFIED");
    }

    @Test
    void aDocumentThatStartedProcessingJustNowIsNotTakenOver() throws Exception {
        TestActors actor = actor();
        UUID claim = claimWithKnownFacts(actor);
        UUID document = uploadOk(actor, claim, pdf(TestPdfs.pdf(1, 9)), "FINAL_INVOICE");
        jdbc.update("update documents set status = 'PROCESSING', updated_at = ? where id = ?", Timestamp.from(Instant.now()), document);

        startExtraction(actor, claim, document).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REQUEST_IN_PROGRESS"));

        assertThat(model.callCount()).isZero();
        assertThat(documentStatus(document)).isEqualTo("PROCESSING");
    }
}
