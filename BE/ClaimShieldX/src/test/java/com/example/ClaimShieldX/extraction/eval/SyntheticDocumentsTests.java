package com.example.ClaimShieldX.extraction.eval;

import com.example.ClaimShieldX.documents.model.DocumentType;
import com.example.ClaimShieldX.extraction.eval.SyntheticDocuments.Sample;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** The evaluation dataset must be sound before any measurement is trusted: right ground truth, deterministic, entirely made up. */
class SyntheticDocumentsTests {
    private static final List<Sample> DATASET = SyntheticDocuments.dataset();

    @Test
    void theDatasetHasTheIntendedSizeAndCoversBothDocumentTypesAndEveryDifficulty() {
        assertThat(DATASET).hasSizeGreaterThanOrEqualTo(32);
        assertThat(DATASET).extracting(Sample::type).contains(DocumentType.FINAL_INVOICE, DocumentType.GARAGE_ESTIMATE);
        assertThat(DATASET).extracting(Sample::group).contains("clean", "scan", "illegible", "multipage", "missing", "altered", "injection", "arithmetic", "crosscheck", "formats", "dense", "borderline", "absent");
        assertThat(DATASET).extracting(Sample::id).doesNotHaveDuplicates();
    }

    @Test
    void generationIsDeterministicSoAMeasurementCanBeRepeated() {
        List<Sample> again = SyntheticDocuments.dataset();

        for (int i = 0; i < DATASET.size(); i++) {
            assertThat(again.get(i).id()).isEqualTo(DATASET.get(i).id());
            if (DATASET.get(i).mimeType().equals("application/pdf")) {
                // PDFs carry a creation timestamp; compare the readable content instead of the raw bytes.
                assertThat(text(again.get(i))).isEqualTo(text(DATASET.get(i)));
            } else {
                assertThat(again.get(i).content()).isEqualTo(DATASET.get(i).content());
            }
        }
    }

    @Test
    void everyFileIsARealPdfOrImageOfAcceptedKindAndWithinTheUploadLimit() {
        for (Sample sample : DATASET) {
            assertThat(sample.mimeType()).isIn("application/pdf", "image/png", "image/jpeg");
            assertThat(sample.content().length).as(sample.id()).isBetween(100, 5_000_000);
            if (sample.mimeType().equals("application/pdf")) {
                assertThat(new String(sample.content(), 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
            }
        }
    }

    @Test
    void theGroundTruthMatchesWhatIsPrintedInTheTextPdfs() throws Exception {
        for (Sample sample : DATASET) {
            if (!sample.mimeType().equals("application/pdf") || sample.group().equals("absent")) {
                continue;
            }
            String printed = text(sample);
            Map<String, String> fields = sample.expected().fields();
            if (fields.get("documentNumber") != null) {
                assertThat(printed).as(sample.id()).contains(fields.get("documentNumber"));
            } else {
                assertThat(printed).as(sample.id()).doesNotContain("Invoice No").doesNotContain("Estimate No");
            }
            if (fields.get("vehicleIdentifier") != null) {
                assertThat(printed).as(sample.id()).contains(fields.get("vehicleIdentifier"));
            } else {
                assertThat(printed).as(sample.id()).doesNotContain("Vehicle:");
            }
            assertThat(printed).as(sample.id()).contains(fields.get("garageIdentifier"));
            for (SyntheticDocuments.Line line : sample.expected().lines()) {
                assertThat(printed).as(sample.id()).contains(line.description());
            }
            if (fields.get("taxTotal") == null) {
                assertThat(printed).as(sample.id()).doesNotContain("GST");
            }
            if (fields.get("subtotal") == null) {
                assertThat(printed).as(sample.id()).doesNotContain("Sub total");
            }
        }
    }

    @Test
    void theArithmeticOfTheGroundTruthIsInternallyConsistentExceptWhereAlterationIsTheTest() {
        for (Sample sample : DATASET) {
            Map<String, String> f = sample.expected().fields();
            if (f.get("grandTotal") == null || sample.group().equals("altered")) {
                continue;
            }
            BigDecimal sum = sample.expected().lines().stream().map(SyntheticDocuments.Line::total).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal discount = f.get("discountTotal") == null ? BigDecimal.ZERO : new BigDecimal(f.get("discountTotal"));
            BigDecimal tax = f.get("taxTotal") == null ? BigDecimal.ZERO : new BigDecimal(f.get("taxTotal"));
            BigDecimal grand = new BigDecimal(f.get("grandTotal"));
            boolean exclusive = sum.subtract(discount).add(tax).subtract(grand).abs().compareTo(new BigDecimal("0.01")) <= 0;
            boolean inclusive = sum.subtract(discount).subtract(grand).abs().compareTo(new BigDecimal("0.01")) <= 0;
            assertThat(exclusive || inclusive).as(sample.id() + " sum=" + sum + " tax=" + tax + " grand=" + grand).isTrue();
        }
    }

    @Test
    void theAbsentSamplesReallyContainNothingAnExtractorCouldReport() throws Exception {
        for (String id : List.of("inv-blank-page", "inv-not-an-invoice")) {
            Sample sample = DATASET.stream().filter(s -> s.id().equals(id)).findFirst().orElseThrow();
            String printed = text(sample);

            assertThat(sample.expected().fields().values()).as(id).containsOnlyNulls();
            assertThat(sample.expected().lines()).as(id).isEmpty();
            assertThat(printed).as(id).doesNotContain("Invoice No").doesNotContain("Total").doesNotContain("GARAGE-").doesNotContain("VEH-").doesNotContainPattern("\\d{2}/\\d{2}/\\d{4}");
        }
        assertThat(text(DATASET.stream().filter(s -> s.id().equals("inv-blank-page")).findFirst().orElseThrow()).strip()).isEmpty();
    }

    @Test
    void theAlteredSampleReallyDisagreesWithItsOwnLines() {
        Sample altered = DATASET.stream().filter(s -> s.id().equals("inv-altered-total")).findFirst().orElseThrow();
        BigDecimal sum = altered.expected().lines().stream().map(SyntheticDocuments.Line::total).reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(new BigDecimal(altered.expected().fields().get("grandTotal"))).isGreaterThan(sum.multiply(new BigDecimal("1.18")).add(new BigDecimal("5000")));
    }

    @Test
    void theMultiPageDocumentsReallyHaveTwoPages() throws Exception {
        for (String id : List.of("inv-multipage", "est-multipage")) {
            Sample sample = DATASET.stream().filter(s -> s.id().equals(id)).findFirst().orElseThrow();
            try (PDDocument document = Loader.loadPDF(sample.content())) {
                assertThat(document.getNumberOfPages()).as(id).isEqualTo(2);
            }
        }
    }

    @Test
    void theIndianFormatSampleReallyUsesIndianGrouping() throws Exception {
        Sample sample = DATASET.stream().filter(s -> s.id().equals("inv-indian-format")).findFirst().orElseThrow();

        assertThat(text(sample)).contains("1,36,650.50").contains("36,250.50").contains("1,61,247.59").contains("Rs.");
    }

    @Test
    void theDatasetContainsNoRealLookingPersonalOrCompanyData() {
        String everything = DATASET.stream().filter(s -> s.mimeType().equals("application/pdf")).map(SyntheticDocumentsTests::text).collect(Collectors.joining("\n"));

        assertThat(everything).contains("demo").contains("Not a real invoice");
        assertThat(everything).doesNotContainPattern("[A-Z]{2}\\d{2}[A-Z]{2}\\d{4}"); // no vehicle registration plates
        assertThat(everything).doesNotContainPattern("\\b\\d{10}\\b"); // no phone-number-like strings
        assertThat(everything).doesNotContain("@");
    }

    private static String text(Sample sample) {
        try (PDDocument document = Loader.loadPDF(sample.content())) {
            return new PDFTextStripper().getText(document);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
