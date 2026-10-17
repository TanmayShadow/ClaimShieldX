package com.example.ClaimShieldX.extraction.eval;

import com.example.ClaimShieldX.documents.model.DocumentStatus;
import com.example.ClaimShieldX.documents.model.DocumentType;
import com.example.ClaimShieldX.extraction.config.ExtractionProperties;
import com.example.ClaimShieldX.extraction.eval.EvaluationScorer.Outcome;
import com.example.ClaimShieldX.extraction.eval.EvaluationScorer.SampleScore;
import com.example.ClaimShieldX.extraction.eval.SyntheticDocuments.Sample;
import com.example.ClaimShieldX.extraction.schema.ModelDocument;
import com.example.ClaimShieldX.extraction.validate.ExtractionProcessor;
import com.example.ClaimShieldX.extraction.validate.ExtractionProcessor.ClaimFacts;
import com.example.ClaimShieldX.extraction.validate.ExtractionProcessor.Processed;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static com.example.ClaimShieldX.extraction.support.TestDocuments.*;
import static org.assertj.core.api.Assertions.assertThat;

/** The scorer decides every accuracy figure we report, so it is tested like any other code. */
class EvaluationScorerTests {
    private static final ExtractionProcessor PROCESSOR = new ExtractionProcessor(ExtractionProperties.defaults());
    private static final ClaimFacts CLAIM = new ClaimFacts(SyntheticDocuments.GARAGE_ID, SyntheticDocuments.VEHICLE_ID, "INR", SyntheticDocuments.INCIDENT);
    private static final Sample CLEAN = SyntheticDocuments.dataset().stream().filter(s -> s.id().equals("inv-clean-pdf")).findFirst().orElseThrow();
    private static final Sample SPARSE = SyntheticDocuments.dataset().stream().filter(s -> s.id().equals("inv-missing-fields")).findFirst().orElseThrow();

    /** A perfect answer for the clean sample. */
    private static ModelDocument perfect() {
        // The lines come from the sample's own ground truth, so this really is a perfect reading of it.
        List<com.example.ClaimShieldX.extraction.schema.ModelLineItem> lines = CLEAN.expected().lines().stream()
                .map(l -> line(l.description(), l.category(), String.valueOf(l.quantity()), l.unitPrice().toPlainString(), l.unitPrice().multiply(java.math.BigDecimal.valueOf(l.quantity())).toPlainString()))
                .toList();
        return with(invoice(SyntheticDocuments.GARAGE_ID, SyntheticDocuments.VEHICLE_ID, "12/09/2026"), b -> b.lines(lines));
    }

    private static Processed run(ModelDocument document) {
        return PROCESSOR.process(document, DocumentType.FINAL_INVOICE, UUID.randomUUID(), 1, CLAIM);
    }

    private static Outcome outcome(SampleScore score, String field) {
        return score.fields().stream().filter(f -> f.name().equals(field)).findFirst().orElseThrow().outcome();
    }

    @Test
    void aPerfectAnswerScoresPerfectly() {
        SampleScore score = EvaluationScorer.score(CLEAN, run(perfect()));

        assertThat(score.fields()).allSatisfy(f -> assertThat(f.outcome()).isIn(Outcome.CORRECT, Outcome.CORRECTLY_ABSENT));
        assertThat(score.lines().attributesCorrect()).isEqualTo(score.lines().attributesExpected()).isEqualTo(12);
        assertThat(score.lines().countMatches()).isTrue();
        assertThat(score.statusAgrees()).isTrue();
    }

    @Test
    void aWrongValueAndAMissedValueAreToldApart() {
        SampleScore score = EvaluationScorer.score(CLEAN, run(with(perfect(), b -> b.grandTotal(f("24,000.00")).currency(none()))));

        assertThat(outcome(score, "grandTotal")).isEqualTo(Outcome.WRONG);
        assertThat(outcome(score, "currency")).isEqualTo(Outcome.MISSED);
        assertThat(outcome(score, "documentNumber")).isEqualTo(Outcome.CORRECT);
    }

    @Test
    void aValueWhereTheDocumentShowsNothingIsInventedNotWrong() {
        // inv-missing-fields prints no vehicle identifier, tax or subtotal. Reporting one is a guess.
        ModelDocument guessed = with(perfect(), b -> b.vehicle(f("VEH-TOKEN-1001")));

        SampleScore score = EvaluationScorer.score(SPARSE, run(guessed));

        assertThat(outcome(score, "vehicleIdentifier")).isEqualTo(Outcome.INVENTED);
        assertThat(outcome(score, "incidentDate")).isEqualTo(Outcome.CORRECTLY_ABSENT);
        assertThat(score.count(Outcome.INVENTED)).isGreaterThanOrEqualTo(1);
    }

    @Test
    void anAbsentFieldReportedAsUnknownIsCorrectlyAbsent() {
        SampleScore score = EvaluationScorer.score(CLEAN, run(perfect()));

        assertThat(outcome(score, "incidentDate")).isEqualTo(Outcome.CORRECTLY_ABSENT);
        assertThat(outcome(score, "discountTotal")).isEqualTo(Outcome.CORRECTLY_ABSENT);
    }

    @Test
    void moneyIsComparedAsNumbersSoFormattingDoesNotCount() {
        SampleScore score = EvaluationScorer.score(CLEAN, run(with(perfect(), b -> b.grandTotal(f("24780")))));

        assertThat(outcome(score, "grandTotal")).isEqualTo(Outcome.CORRECT);
    }

    @Test
    void lineItemsAreScoredPerAttributeAndByCount() {
        ModelDocument oneWrong = with(perfect(), b -> b.lines(List.of(line("Front bumper assembly", "Part", "1", "12,000.00", "12,000.00"),
                line("Paint and finish", "Paint", "1", "4,500.00", "9,999.00"), line("Labour charges", "Labour", "3", "1,500.00", "4,500.00"))));
        ModelDocument oneMissing = with(perfect(), b -> b.lines(List.of(line("Front bumper assembly", "Part", "1", "12,000.00", "12,000.00"))));

        assertThat(EvaluationScorer.score(CLEAN, run(oneWrong)).lines().attributesCorrect()).isEqualTo(11);
        assertThat(EvaluationScorer.score(CLEAN, run(oneMissing)).lines().attributesCorrect()).isEqualTo(4);
        assertThat(EvaluationScorer.score(CLEAN, run(oneMissing)).lines().countMatches()).isFalse();
    }

    @Test
    void descriptionsAreComparedIgnoringCaseAndPunctuation() {
        ModelDocument document = with(perfect(), b -> b.lines(List.of(line("FRONT BUMPER, ASSEMBLY", "Parts", "1", "12,000.00", "12,000.00"),
                line("paint, and finish.", "Paint", "1", "4,500.00", "4,500.00"), line("Labour charges", "Labour", "3", "1,500.00", "4,500.00"))));

        assertThat(EvaluationScorer.score(CLEAN, run(document)).lines().attributesCorrect()).isEqualTo(12);
    }

    @Test
    void aWrongValueThatIsVerifiedIsTheUnsafeCaseAndAReviewIsNever() {
        ModelDocument wrongGrandTotalButArithmeticallyConsistent = with(perfect(), b -> b.grandTotal(f("24,780.50")).subtotal(f("21,000.00")));
        Processed verified = run(wrongGrandTotalButArithmeticallyConsistent); // within the 1.00 sanity tolerance, so it passes the checks

        assertThat(verified.status()).isEqualTo(DocumentStatus.VERIFIED);
        assertThat(EvaluationScorer.score(CLEAN, verified).unsafelyVerified()).as("a wrong value that was verified").isTrue();

        Processed reviewed = run(with(perfect(), b -> b.garage(f("OTHER-GARAGE")).grandTotal(f("24,780.50"))));
        assertThat(reviewed.status()).isEqualTo(DocumentStatus.NEEDS_REVIEW);
        assertThat(EvaluationScorer.score(CLEAN, reviewed).unsafelyVerified()).as("a wrong value sent for review").isFalse();
    }

    @Test
    void aPerfectVerifiedDocumentAndAnUnknownValueAreNotUnsafe() {
        assertThat(EvaluationScorer.score(CLEAN, run(perfect())).unsafelyVerified()).isFalse();
        // An honest unknown is not wrong: the currency is missing, so the document needs review, and nothing false is claimed.
        assertThat(EvaluationScorer.score(CLEAN, run(with(perfect(), b -> b.currency(none())))).unsafelyVerified()).isFalse();
    }

    @Test
    void aSampleWithNoExpectedStatusAcceptsEitherOutcome() {
        Sample borderline = SyntheticDocuments.dataset().stream().filter(s -> s.id().equals("inv-faint-scan")).findFirst().orElseThrow();

        assertThat(borderline.expected().status()).isNull();
        assertThat(EvaluationScorer.score(borderline, run(perfect())).statusAgrees()).isTrue();
        assertThat(EvaluationScorer.score(borderline, run(with(perfect(), b -> b.garage(f("X"))))).statusAgrees()).isTrue();
    }

    @Test
    void theStatusAndTheExpectedIssuesAreChecked() {
        Sample altered = SyntheticDocuments.dataset().stream().filter(s -> s.id().equals("inv-altered-total")).findFirst().orElseThrow();
        Processed verifiedWronglyForAltered = run(perfect());

        SampleScore score = EvaluationScorer.score(altered, verifiedWronglyForAltered);

        assertThat(score.expectedStatus()).isEqualTo(DocumentStatus.NEEDS_REVIEW);
        assertThat(score.actualStatus()).isEqualTo(DocumentStatus.VERIFIED);
        assertThat(score.statusAgrees()).isFalse();
        assertThat(score.expectedIssuesNotRaised()).containsExactly("GRAND_TOTAL_MISMATCH");
    }
}
