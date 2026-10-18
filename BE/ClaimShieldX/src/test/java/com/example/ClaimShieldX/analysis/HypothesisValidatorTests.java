package com.example.ClaimShieldX.analysis;

import com.example.ClaimShieldX.analysis.reasoning.HypothesisValidator;
import com.example.ClaimShieldX.analysis.reasoning.HypothesisValidator.Hypothesis;
import com.example.ClaimShieldX.analysis.reasoning.Outputs;
import com.example.ClaimShieldX.analysis.snapshot.SnapshotService.Item;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Java, not the model, decides what may be shown: every reference must exist, the wording must be neutral, and a hypothesis needs evidence that shows something. */
class HypothesisValidatorTests {
    private static final Map<String, Item> EVIDENCE = new LinkedHashMap<>();

    static {
        add(new Item("RULE.INVOICE_ARITHMETIC", "RULE_RESULT", "DETERMINISTIC_RESULT", "Invoice arithmetic", Map.of("outcome", "FLAG")));
        add(new Item("CLAIM.stage", "CLAIM_FIELD", "VERIFIED_FACT", "Claim stage", Map.of()));
        add(new Item("GAP.DATE_CONSISTENCY", "UNKNOWN", "UNKNOWN", "Date check could not run", Map.of()));
        add(new Item("CHECK.FINAL_INVOICE.REQUIRED", "CHECKLIST_ITEM", "UNKNOWN", "Invoice required", Map.of()));
        add(new Item("RULE.DATE_CONSISTENCY", "RULE_RESULT", "DETERMINISTIC_RESULT", "Dates", Map.of("outcome", "INCOMPLETE")));
        add(new Item("RULE.DOCUMENT_FILE_DUPLICATE", "RULE_RESULT", "DETERMINISTIC_RESULT", "Same file", Map.of("outcome", "PASS")));
        add(new Item("HIST.abcd1234.1", "HISTORICAL_RECORD", "CONTEXT", "An earlier claim", Map.of()));
    }

    private static void add(Item item) {
        EVIDENCE.put(item.ref(), item);
    }

    private static Outputs.Concern concern(String title, String explanation, String... refs) {
        return new Outputs.Concern(title, explanation, List.of(refs), List.of("Ask the garage for the original invoice"), List.of());
    }

    private static HypothesisValidator.H1Result h1(Outputs.Concern... concerns) {
        return HypothesisValidator.validateH1(new Outputs.H1(List.of(concerns)), EVIDENCE);
    }

    private static List<String> reasons(HypothesisValidator.H1Result result) {
        return result.rejected().stream().map(HypothesisValidator.Rejected::reason).toList();
    }

    @Test
    void aHypothesisCitingRealEvidenceThatShowsSomethingIsAccepted() {
        var result = h1(concern("Printed total differs", "The printed total differs from the sum of the lines.", "RULE.INVOICE_ARITHMETIC", "CLAIM.stage"));

        assertThat(result.rejected()).isEmpty();
        assertThat(result.hypotheses()).singleElement().satisfies(h -> {
            assertThat(h.id()).isEqualTo("H-1");
            assertThat(h.supportingRefs()).containsExactly("RULE.INVOICE_ARITHMETIC", "CLAIM.stage");
        });
    }

    @Test
    void aReferenceThatIsNotInTheSnapshotRejectsTheHypothesis() {
        assertThat(reasons(h1(concern("Invented", "Rests on something that is not there.", "RULE.INVOICE_ARITHMETIC", "RULE.MADE_UP")))).containsExactly("UNKNOWN_REFERENCE");
    }

    @Test
    void aHypothesisWithoutReferencesIsRejected() {
        assertThat(reasons(h1(concern("Unsupported", "Rests on nothing at all.")))).containsExactly("NO_SUPPORTING_EVIDENCE");
    }

    @Test
    void aHypothesisThatRestsOnlyOnMissingEvidenceOrOnRulesThatFlaggedNothingIsRejected() {
        assertThat(reasons(h1(concern("Missing", "Rests only on what is absent.", "GAP.DATE_CONSISTENCY", "CHECK.FINAL_INVOICE.REQUIRED")))).containsExactly("NO_FLAGGED_EVIDENCE");
        assertThat(reasons(h1(concern("Incomplete", "Rests on a check that could not run.", "RULE.DATE_CONSISTENCY", "GAP.DATE_CONSISTENCY")))).containsExactly("NO_FLAGGED_EVIDENCE");
        assertThat(reasons(h1(concern("Passed", "Rests on a rule that passed.", "RULE.DOCUMENT_FILE_DUPLICATE", "CLAIM.stage")))).containsExactly("NO_FLAGGED_EVIDENCE");
    }

    @Test
    void anEarlierRelatedClaimIsEnoughToRestAConcernOn() {
        assertThat(h1(concern("Earlier claim", "An earlier claim relates to this one.", "HIST.abcd1234.1")).hypotheses()).hasSize(1);
    }

    @Test
    void missingEvidenceMayAccompanyEvidenceThatShowsSomething() {
        var result = h1(concern("Both", "A flagged result and a gap together.", "RULE.INVOICE_ARITHMETIC", "GAP.DATE_CONSISTENCY"));
        assertThat(result.hypotheses()).hasSize(1);
    }

    @Test
    void wordingThatAccusesIsRejectedWhateverTheEvidence() {
        for (String word : List.of("fraud", "Fraudulent", "scam", "forged", "fabricated", "criminal", "cheating")) {
            assertThat(reasons(h1(concern("Concern " + word, "This looks like " + word + " to me.", "RULE.INVOICE_ARITHMETIC")))).as(word).containsExactly("NON_NEUTRAL_WORDING");
        }
        assertThat(reasons(h1(new Outputs.Concern("Neutral title", "Neutral text.", List.of("RULE.INVOICE_ARITHMETIC"), List.of("Check for fraud"), List.of())))).containsExactly("NON_NEUTRAL_WORDING");
    }

    @Test
    void emptyTextIsRejected() {
        assertThat(reasons(h1(concern(" ", "Text.", "RULE.INVOICE_ARITHMETIC"), concern("Title", "", "RULE.INVOICE_ARITHMETIC")))).containsExactly("EMPTY_TEXT", "EMPTY_TEXT");
    }

    @Test
    void atMostFourHypothesesAreConsideredAndTheyAreNumberedWithoutGaps() {
        List<Outputs.Concern> many = new ArrayList<>();
        many.add(concern("Rejected first", "Has no evidence.", "RULE.MADE_UP"));
        for (int i = 1; i <= 6; i++) {
            many.add(concern("Concern " + i, "Explanation " + i + ".", "RULE.INVOICE_ARITHMETIC"));
        }
        var result = HypothesisValidator.validateH1(new Outputs.H1(many), EVIDENCE);

        assertThat(result.hypotheses()).extracting(Hypothesis::id).containsExactly("H-1", "H-2", "H-3");
        assertThat(result.rejected()).hasSize(1);
    }

    @Test
    void controlCharactersAndLongTextAreCleaned() {
        var result = h1(concern("Title\u0000 with\u0007 controls", "x".repeat(5000), "RULE.INVOICE_ARITHMETIC"));
        assertThat(result.hypotheses()).singleElement().satisfies(h -> {
            assertThat(h.title()).doesNotContain("\u0000").doesNotContain("\u0007");
            assertThat(h.explanation().length()).isLessThanOrEqualTo(600);
        });
    }

    @Test
    void counterEvidenceKeepsOnlyKnownReferencesAndKnownHypotheses() {
        var hypotheses = h1(concern("A concern", "Explanation here.", "RULE.INVOICE_ARITHMETIC")).hypotheses();
        var out = new Outputs.H2(List.of(
                new Outputs.Assessment("H-1", List.of(new Outputs.Legitimate("A rounding difference is common.", List.of("CLAIM.stage", "NOPE"), List.of("Compare with the original"))), List.of("CLAIM.stage", "ALSO.NOPE")),
                new Outputs.Assessment("H-9", List.of(), List.of()),
                new Outputs.Assessment("H-1", List.of(), List.of())));

        var result = HypothesisValidator.validateH2(out, hypotheses, EVIDENCE.keySet());

        assertThat(result.assessments()).hasSize(1);
        assertThat(result.assessments().get(0).counterEvidenceRefs()).containsExactly("CLAIM.stage");
        assertThat(result.assessments().get(0).legitimate().get(0).supportingRefs()).containsExactly("CLAIM.stage");
        assertThat(result.droppedReferences()).isEqualTo(2);
    }

    @Test
    void aLegitimateExplanationThatAccusesIsDropped() {
        var hypotheses = h1(concern("A concern", "Explanation here.", "RULE.INVOICE_ARITHMETIC")).hypotheses();
        var out = new Outputs.H2(List.of(new Outputs.Assessment("H-1",
                List.of(new Outputs.Legitimate("The garage committed fraud.", List.of(), List.of()), new Outputs.Legitimate("A rounding difference is common.", List.of(), List.of())), List.of())));

        assertThat(HypothesisValidator.validateH2(out, hypotheses, Set.of()).assessments().get(0).legitimate()).extracting(HypothesisValidator.Legitimate::explanation)
                .containsExactly("A rounding difference is common.");
    }

    @Test
    void theReviewerMayOnlyUseTheThreeSupportLevelsAndTheKnownHypotheses() {
        var hypotheses = h1(concern("A concern", "Explanation here.", "RULE.INVOICE_ARITHMETIC")).hypotheses();
        var out = new Outputs.H3(List.of(
                new Outputs.Review("H-1", "CERTAIN", List.of("x"), List.of()),
                new Outputs.Review("H-7", "SUPPORTED", List.of("x"), List.of()),
                new Outputs.Review("H-1", "PARTIALLY_SUPPORTED", List.of("Only one of two checks agrees."), List.of("RULE.INVOICE_ARITHMETIC", "NOPE")),
                new Outputs.Review("H-1", "SUPPORTED", List.of("Second answer for the same one."), List.of())));

        var result = HypothesisValidator.validateH3(out, hypotheses, EVIDENCE.keySet());

        assertThat(result.reviews()).singleElement().satisfies(r -> {
            assertThat(r.support()).isEqualTo("PARTIALLY_SUPPORTED");
            assertThat(r.checkedRefs()).containsExactly("RULE.INVOICE_ARITHMETIC");
        });
        assertThat(result.droppedReferences()).isEqualTo(1);
    }

    @Test
    void theModelsOutputMustMatchTheExpectedShapeExactly() {
        var parser = Outputs.parserFor(Outputs.H1.class);
        org.junit.jupiter.api.Assertions.assertThrows(com.example.ClaimShieldX.analysis.reasoning.ReasoningCaller.MalformedOutput.class, () -> parser.parse("{\"hypotheses\":[],\"verdict\":\"guilty\"}"));
        org.junit.jupiter.api.Assertions.assertThrows(com.example.ClaimShieldX.analysis.reasoning.ReasoningCaller.MalformedOutput.class, () -> parser.parse("not json"));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> parser.parse("{\"hypotheses\":[]}"));
    }
}
