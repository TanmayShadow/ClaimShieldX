package com.example.ClaimShieldX.extraction.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PdfPagesTests {
    private static byte[] pdf(int pages, String userPassword) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (int i = 0; i < pages; i++) {
                document.addPage(new PDPage());
            }
            if (userPassword != null) {
                StandardProtectionPolicy policy = new StandardProtectionPolicy("owner-secret", userPassword, new AccessPermission());
                policy.setEncryptionKeyLength(128);
                document.protect(policy);
            }
            document.save(out);
            return out.toByteArray();
        }
    }

    @Test
    void countsThePagesOfARealPdf() throws Exception {
        assertThat(PdfPages.count(pdf(1, null))).isEqualTo(1);
        assertThat(PdfPages.count(pdf(7, null))).isEqualTo(7);
    }

    @Test
    void aPdfWithNoPagesIsUnreadable() throws Exception {
        assertThatThrownBy(() -> PdfPages.count(pdf(0, null))).isInstanceOf(PdfPages.UnreadablePdfException.class);
    }

    @Test
    void garbageTruncatedAndEmptyInputAreUnreadable() throws Exception {
        byte[] good = pdf(2, null);

        assertThatThrownBy(() -> PdfPages.count("not a pdf".getBytes(StandardCharsets.US_ASCII))).isInstanceOf(PdfPages.UnreadablePdfException.class);
        assertThatThrownBy(() -> PdfPages.count(new byte[0])).isInstanceOf(PdfPages.UnreadablePdfException.class);
        assertThatThrownBy(() -> PdfPages.count("%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII))).isInstanceOf(PdfPages.UnreadablePdfException.class);
        assertThatThrownBy(() -> PdfPages.count(java.util.Arrays.copyOf(good, good.length / 3))).isInstanceOf(PdfPages.UnreadablePdfException.class);
    }

    @Test
    void aPdfThatNeedsAPasswordIsUnreadable() throws Exception {
        assertThatThrownBy(() -> PdfPages.count(pdf(1, "needs-a-password"))).isInstanceOf(PdfPages.UnreadablePdfException.class);
    }
}
