package com.example.ClaimShieldX.extraction.eval;

import com.example.ClaimShieldX.documents.model.DocumentStatus;
import com.example.ClaimShieldX.extraction.eval.SyntheticDocuments.Line;
import com.example.ClaimShieldX.extraction.eval.SyntheticDocuments.Sample;
import com.example.ClaimShieldX.extraction.schema.FieldResult;
import com.example.ClaimShieldX.extraction.schema.FieldState;
import com.example.ClaimShieldX.extraction.schema.IssueCode;
import com.example.ClaimShieldX.extraction.schema.LineItemResult;
import com.example.ClaimShieldX.extraction.validate.ExtractionProcessor.Processed;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Compares one extraction with the known truth of a synthetic document. A field the document does not show must come back
 * UNKNOWN: a value there is INVENTED, which is the failure that matters most for a tool that must never guess.
 */
public final class EvaluationScorer {
    private static final Set<String> MONEY_FIELDS = Set.of("subtotal", "taxTotal", "discountTotal", "grandTotal");

    public enum Outcome { CORRECT, WRONG, MISSED, INVENTED, CORRECTLY_ABSENT }

    public record FieldScore(String name, String expected, String actual, Outcome outcome) {
    }

    public record LineScore(int expectedLines, int actualLines, int attributesExpected, int attributesCorrect) {
        public boolean countMatches() {
            return expectedLines == actualLines;
        }
    }

    /**
     * @param expectedStatus null when either outcome is acceptable and the test is only that nothing wrong gets verified
     *                       (borderline scans: they may be read correctly or sent for review)
     */
    public record SampleScore(String id, List<FieldScore> fields, LineScore lines, DocumentStatus expectedStatus, DocumentStatus actualStatus,
                              List<String> expectedIssuesNotRaised) {
        public boolean statusAgrees() {
            return expectedStatus == null || expectedStatus == actualStatus;
        }

        /**
         * The failure that matters most: the document was marked VERIFIED although a value was wrong or made up, or a line
         * was misread. A document sent for review is never unsafe; a value reported as unknown is honest, not wrong.
         */
        public boolean unsafelyVerified() {
            boolean somethingWrong = count(Outcome.WRONG) > 0 || count(Outcome.INVENTED) > 0 || lines.attributesCorrect() < lines.attributesExpected();
            return actualStatus == DocumentStatus.VERIFIED && somethingWrong;
        }

        public long count(Outcome outcome) {
            return fields.stream().filter(f -> f.outcome() == outcome).count();
        }
    }

    private EvaluationScorer() {
    }

    public static SampleScore score(Sample sample, Processed processed) {
        Map<String, FieldResult> actual = processed.result().fields().stream().collect(Collectors.toMap(FieldResult::name, f -> f));
        List<FieldScore> fields = new ArrayList<>();
        for (Map.Entry<String, String> expected : sample.expected().fields().entrySet()) {
            FieldResult result = actual.get(expected.getKey());
            String value = result == null || result.state() != FieldState.EXTRACTED ? null : result.normalizedValue();
            boolean extracted = result != null && result.state() == FieldState.EXTRACTED;
            fields.add(new FieldScore(expected.getKey(), expected.getValue(), value, outcome(expected.getKey(), expected.getValue(), value, extracted)));
        }

        List<LineItemResult> lines = processed.result().lineItems();
        int correct = 0;
        for (int i = 0; i < sample.expected().lines().size() && i < lines.size(); i++) {
            Line expected = sample.expected().lines().get(i);
            Map<String, FieldResult> byName = lines.get(i).fields().stream().collect(Collectors.toMap(f -> f.name().substring(f.name().indexOf('.') + 1), f -> f));
            correct += sameText(expected.description(), normalized(byName.get("description"))) ? 1 : 0;
            correct += sameNumber(BigDecimal.valueOf(expected.quantity()).toPlainString(), normalized(byName.get("quantity"))) ? 1 : 0;
            correct += sameNumber(expected.unitPrice().toPlainString(), normalized(byName.get("unitPrice"))) ? 1 : 0;
            correct += sameNumber(expected.total().toPlainString(), normalized(byName.get("lineTotal"))) ? 1 : 0;
        }
        LineScore lineScore = new LineScore(sample.expected().lines().size(), lines.size(), sample.expected().lines().size() * 4, correct);

        Set<String> raised = processed.result().issues().stream().map(i -> i.code().name()).collect(Collectors.toSet());
        List<String> notRaised = sample.expected().expectedIssues().stream().filter(code -> !raised.contains(code)).toList();
        return new SampleScore(sample.id(), fields, lineScore, sample.expected().status(), processed.status(), notRaised);
    }

    private static String normalized(FieldResult field) {
        return field == null || field.state() != FieldState.EXTRACTED ? null : field.normalizedValue();
    }

    private static Outcome outcome(String name, String expected, String actual, boolean extracted) {
        if (expected == null) {
            return extracted ? Outcome.INVENTED : Outcome.CORRECTLY_ABSENT;
        }
        if (actual == null) {
            return Outcome.MISSED;
        }
        // A discount printed as (1,000.00) or -1,000.00 reads as a negative number; its size is what is compared.
        boolean same = name.equals("discountTotal") ? sameNumber(new BigDecimal(expected).abs().toPlainString(), actual == null ? null : new BigDecimal(actual).abs().toPlainString())
                : MONEY_FIELDS.contains(name) ? sameNumber(expected, actual) : expected.equals(actual);
        return same ? Outcome.CORRECT : Outcome.WRONG;
    }

    private static boolean sameNumber(String expected, String actual) {
        if (actual == null) {
            return false;
        }
        try {
            return new BigDecimal(expected).compareTo(new BigDecimal(actual)) == 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean sameText(String expected, String actual) {
        return actual != null && squash(expected).equals(squash(actual));
    }

    private static String squash(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").strip();
    }

    /** Which issue codes appear at all, for reporting. */
    public static Set<IssueCode> codes(Processed processed) {
        return processed.result().issues().stream().map(i -> i.code()).collect(Collectors.toSet());
    }
}
