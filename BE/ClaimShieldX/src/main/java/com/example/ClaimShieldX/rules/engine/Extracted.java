package com.example.ClaimShieldX.rules.engine;

import com.example.ClaimShieldX.extraction.schema.ExtractionResult;
import com.example.ClaimShieldX.extraction.schema.FieldResult;
import com.example.ClaimShieldX.extraction.schema.FieldState;
import com.example.ClaimShieldX.extraction.schema.IssueCode;
import com.example.ClaimShieldX.extraction.schema.IssueSeverity;
import com.example.ClaimShieldX.extraction.schema.LineItemResult;
import com.example.ClaimShieldX.extraction.schema.Source;
import com.example.ClaimShieldX.rules.engine.RuleInputs.DocumentFacts;
import com.example.ClaimShieldX.rules.model.EvidenceRef;
import com.example.ClaimShieldX.rules.model.InputReliability;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Reads the typed values of one stored extraction. A value is "known" only when the extraction found it AND Java could
 * normalize it; anything else is unknown and a rule must say so rather than guess. Nothing here calls a model.
 */
final class Extracted {
    /** Why a rule may not trust what it read: anything that makes the read doubtful, apart from what the rule itself reports. */
    private static final Set<IssueCode> NEVER_DOUBT = Set.of(IssueCode.DATE_FORMAT_ASSUMED);

    record Line(int index, FieldResult description, FieldResult category, FieldResult quantity, FieldResult unitPrice, FieldResult tax, FieldResult lineTotal) {
        BigDecimal quantityValue() {
            return amount(quantity);
        }

        BigDecimal unitPriceValue() {
            return amount(unitPrice);
        }

        BigDecimal taxValue() {
            return amount(tax);
        }

        BigDecimal lineTotalValue() {
            return amount(lineTotal);
        }

        String descriptionText() {
            return known(description) ? description.normalizedValue() : null;
        }

        /** The row's page and quote are shared by its cells, so any one of them points at the row. */
        Source source() {
            for (FieldResult field : new FieldResult[]{lineTotal, description, unitPrice, quantity}) {
                if (field != null && field.source() != null) {
                    return field.source();
                }
            }
            return null;
        }
    }

    private final DocumentFacts doc;
    private final ExtractionResult result;
    private final Map<String, FieldResult> fields = new HashMap<>();
    private final List<Line> lines = new ArrayList<>();

    private Extracted(DocumentFacts doc, ExtractionResult result) {
        this.doc = doc;
        this.result = result;
        if (result.fields() != null) {
            result.fields().forEach(f -> fields.put(f.name(), f));
        }
        if (result.lineItems() != null) {
            for (LineItemResult item : result.lineItems()) {
                Map<String, FieldResult> cells = new HashMap<>();
                item.fields().forEach(f -> cells.put(f.name().substring(f.name().indexOf('.') + 1), f));
                lines.add(new Line(item.index(), cells.get("description"), cells.get("category"), cells.get("quantity"),
                        cells.get("unitPrice"), cells.get("tax"), cells.get("lineTotal")));
            }
        }
    }

    /** Null when the document has no usable extraction (never read, or the newest attempt failed). */
    static Extracted of(DocumentFacts doc) {
        if (doc == null || doc.extraction() == null || doc.extraction().result() == null) {
            return null;
        }
        return new Extracted(doc, doc.extraction().result());
    }

    DocumentFacts document() {
        return doc;
    }

    List<Line> lines() {
        return lines;
    }

    FieldResult field(String name) {
        return fields.get(name);
    }

    BigDecimal amount(String name) {
        return amount(fields.get(name));
    }

    LocalDate date(String name) {
        FieldResult field = fields.get(name);
        if (!known(field)) {
            return null;
        }
        try {
            return LocalDate.parse(field.normalizedValue());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    String text(String name) {
        FieldResult field = fields.get(name);
        return known(field) ? field.normalizedValue() : null;
    }

    /** True when the document shows nothing for this field (as opposed to a value that could not be read). */
    boolean printedAsAbsent(String name) {
        FieldResult field = fields.get(name);
        return field != null && field.state() == FieldState.UNKNOWN && "NOT_PRESENT".equals(field.unknownReason());
    }

    boolean formatAssumed(String name) {
        FieldResult field = fields.get(name);
        return field != null && field.normalizationNote() != null
                && (field.normalizationNote().contains("DAY_FIRST_ASSUMED") || field.normalizationNote().contains("TWO_DIGIT_YEAR"));
    }

    /** The text exactly as printed, used only to test whether day and month could have been read the other way round. */
    String printed(String name) {
        FieldResult field = fields.get(name);
        return field == null ? null : field.value();
    }

    EvidenceRef ref(String name) {
        FieldResult field = fields.get(name);
        Source source = field == null ? null : field.source();
        return EvidenceRef.documentField(doc.id(), doc.extraction().extractionId(), name, source == null ? null : source.page(), source == null ? null : source.quote());
    }

    EvidenceRef lineRef(Line line) {
        Source source = line.source();
        return EvidenceRef.documentField(doc.id(), doc.extraction().extractionId(), "lineItems[" + line.index() + "]",
                source == null ? null : source.page(), source == null ? null : source.quote());
    }

    /** The reasons this document's values may be misread, leaving out the issue codes the calling rule itself reports. */
    List<String> doubts(Set<IssueCode> ownFindings) {
        TreeSet<String> reasons = new TreeSet<>();
        if (result.issues() != null) {
            result.issues().stream()
                    .filter(i -> i.severity() == IssueSeverity.REVIEW)
                    .filter(i -> !ownFindings.contains(i.code()) && !NEVER_DOUBT.contains(i.code()))
                    .forEach(i -> reasons.add(i.code().name()));
        }
        return List.copyOf(reasons);
    }

    static InputReliability reliability(List<String> doubts) {
        return doubts.isEmpty() ? InputReliability.RELIABLE : InputReliability.DOUBTFUL;
    }

    static boolean known(FieldResult field) {
        return field != null && field.state() == FieldState.EXTRACTED && field.normalizedValue() != null;
    }

    static BigDecimal amount(FieldResult field) {
        if (!known(field)) {
            return null;
        }
        try {
            return new BigDecimal(field.normalizedValue());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
