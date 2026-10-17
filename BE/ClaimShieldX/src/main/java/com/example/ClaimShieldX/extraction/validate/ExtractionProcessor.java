package com.example.ClaimShieldX.extraction.validate;

import com.example.ClaimShieldX.documents.model.DocumentStatus;
import com.example.ClaimShieldX.documents.model.DocumentType;
import com.example.ClaimShieldX.extraction.config.ExtractionProperties;
import com.example.ClaimShieldX.extraction.normalize.AmountParser;
import com.example.ClaimShieldX.extraction.normalize.CurrencyNormalizer;
import com.example.ClaimShieldX.extraction.normalize.DateParser;
import com.example.ClaimShieldX.extraction.normalize.TextCleaner;
import com.example.ClaimShieldX.extraction.schema.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Turns the raw answer of the model into the result that is stored, and decides what the document's status becomes.
 * Pure and deterministic: no database, no model, no clock.
 *
 * <p>Nothing the model says is trusted. Every value is normalized in Java with the original text kept beside it, cited
 * pages are checked against the real page count, the totals are recalculated here, and the identifiers and dates are
 * compared with the claim record. The claim record is never sent to the model, so the model cannot echo it back to pass
 * a check. A document is VERIFIED only when no check raised a REVIEW issue; otherwise it is NEEDS_REVIEW. A problem
 * here is a reason for a person to look, never a finding about the claim.
 */
public final class ExtractionProcessor {
    private static final int MAX_ISSUES = 100;
    private static final int QUOTE_MAX = 300;

    private static final Set<String> REPORTED_CODES = Set.of("ILLEGIBLE_REGION", "HANDWRITTEN_ALTERATION", "INSTRUCTION_LIKE_TEXT", "OTHER_PROBLEM");

    private enum Kind { TEXT, IDENTIFIER, DATE, MONEY, QUANTITY, CURRENCY, CATEGORY }

    /** The claim's own facts used for cross-checks. They are compared locally and never leave the application. */
    public record ClaimFacts(String garageId, String vehicleToken, String currency, Instant incidentAt) {
    }

    public record Processed(ExtractionResult result, DocumentStatus status) {
    }

    private record Built(FieldResult result, BigDecimal amount, LocalDate date, String text) {
        boolean known() {
            return result.state() == FieldState.EXTRACTED;
        }
    }

    private record Line(int index, Built description, Built category, Built quantity, Built unitPrice, Built tax, Built lineTotal) {
    }

    private final int maxLineItems;
    private final BigDecimal tolerance;
    private final ZoneId zone;

    public ExtractionProcessor(ExtractionProperties properties) {
        this.maxLineItems = properties.maxLineItems();
        this.tolerance = properties.arithmeticTolerance();
        this.zone = properties.documentTimeZone();
    }

    public Processed process(ModelDocument model, DocumentType type, UUID documentId, int pageCount, ClaimFacts claim) {
        Run run = new Run(documentId, pageCount);
        ModelHeader header = model.header();
        ModelTotals totals = model.totals();

        Built documentNumber = run.field("documentNumber", header == null ? null : header.documentNumber(), Kind.IDENTIFIER, true);
        Built documentDate = run.field("documentDate", header == null ? null : header.documentDate(), Kind.DATE, true);
        Built incidentDate = run.field("incidentDate", header == null ? null : header.incidentDate(), Kind.DATE, true);
        Built repairStart = run.field("repairStartDate", header == null ? null : header.repairStartDate(), Kind.DATE, true);
        Built repairEnd = run.field("repairCompletionDate", header == null ? null : header.repairCompletionDate(), Kind.DATE, true);
        Built garage = run.field("garageIdentifier", header == null ? null : header.garageIdentifier(), Kind.IDENTIFIER, true);
        Built vehicle = run.field("vehicleIdentifier", header == null ? null : header.vehicleIdentifier(), Kind.IDENTIFIER, true);
        Built currency = run.field("currency", header == null ? null : header.currency(), Kind.CURRENCY, true);
        Built repairDescription = run.field("repairDescription", header == null ? null : header.repairDescription(), Kind.TEXT, false);

        Built subtotal = run.field("subtotal", totals == null ? null : totals.subtotal(), Kind.MONEY, true);
        Built taxTotal = run.field("taxTotal", totals == null ? null : totals.taxTotal(), Kind.MONEY, true);
        Built discountTotal = run.field("discountTotal", totals == null ? null : totals.discountTotal(), Kind.MONEY, true);
        Built grandTotal = run.field("grandTotal", totals == null ? null : totals.grandTotal(), Kind.MONEY, true);

        List<ModelLineItem> modelLines = model.lineItems() == null ? List.of() : model.lineItems();
        if (modelLines.size() > maxLineItems) {
            run.issue(IssueCode.TOO_MANY_LINE_ITEMS, IssueSeverity.REVIEW, "lineItems",
                    "The document has more than " + maxLineItems + " line items; only the first " + maxLineItems + " were kept.");
            modelLines = modelLines.subList(0, maxLineItems);
        }
        List<Line> lines = new ArrayList<>();
        for (int i = 0; i < modelLines.size(); i++) {
            lines.add(run.line(i, modelLines.get(i)));
        }

        List<ReportedWarning> reported = run.reported(model.warnings());

        checkRequired(run, type, documentNumber, documentDate, currency, grandTotal, lines);
        ComputedTotals computed = checkArithmetic(run, lines, subtotal, taxTotal, discountTotal, grandTotal);
        checkAgainstClaim(run, claim, garage, vehicle, currency, documentDate, incidentDate);
        checkRepairDates(run, repairStart, repairEnd);
        if (!documentDate.known() && !grandTotal.known() && lines.isEmpty()) {
            run.issue(IssueCode.UNREADABLE_DOCUMENT, IssueSeverity.REVIEW, null,
                    "No date, total or line item could be read from this document. It may be unreadable or the wrong kind of document.");
        }

        List<FieldResult> fields = List.of(documentNumber.result(), documentDate.result(), incidentDate.result(), repairStart.result(),
                repairEnd.result(), garage.result(), vehicle.result(), currency.result(), repairDescription.result(),
                subtotal.result(), taxTotal.result(), discountTotal.result(), grandTotal.result());
        List<LineItemResult> lineResults = lines.stream().map(l -> new LineItemResult(l.index(), List.of(l.description().result(),
                l.category().result(), l.quantity().result(), l.unitPrice().result(), l.tax().result(), l.lineTotal().result()))).toList();

        ExtractionResult result = new ExtractionResult(fields, lineResults, computed, reported, List.copyOf(run.issues));
        boolean needsReview = run.issues.stream().anyMatch(i -> i.severity() == IssueSeverity.REVIEW);
        return new Processed(result, needsReview ? DocumentStatus.NEEDS_REVIEW : DocumentStatus.VERIFIED);
    }

    // ------------------------------------------------------------ checks

    private static void checkRequired(Run run, DocumentType type, Built documentNumber, Built documentDate, Built currency, Built grandTotal, List<Line> lines) {
        if (type == DocumentType.FINAL_INVOICE) {
            run.requireKnown(documentNumber, "invoice number");
        }
        run.requireKnown(documentDate, "document date");
        run.requireKnown(currency, "currency");
        run.requireKnown(grandTotal, "grand total");
        if (lines.isEmpty()) {
            run.issue(IssueCode.REQUIRED_FIELD_UNKNOWN, IssueSeverity.REVIEW, "lineItems", "No line items could be read from the document.");
        }
        for (Line line : lines) {
            if (!line.lineTotal().known()) {
                run.issue(IssueCode.REQUIRED_FIELD_UNKNOWN, IssueSeverity.REVIEW, "lineItems[" + line.index() + "].lineTotal",
                        "The amount of line " + (line.index() + 1) + " could not be read.");
            }
        }
    }

    /**
     * Totals are recalculated here. The tax column is allowed to be inside or outside the line amounts (both are common on
     * invoices), so the grand total may equal subtotal - discount + tax, or subtotal - discount.
     */
    private ComputedTotals checkArithmetic(Run run, List<Line> lines, Built subtotal, Built taxTotal, Built discountTotal, Built grandTotal) {
        for (Line line : lines) {
            if (line.quantity().amount() != null && line.unitPrice().amount() != null && line.lineTotal().amount() != null) {
                BigDecimal product = line.quantity().amount().multiply(line.unitPrice().amount());
                boolean matches = within(product, line.lineTotal().amount())
                        || (line.tax().amount() != null && within(product.add(line.tax().amount()), line.lineTotal().amount()));
                if (!matches) {
                    run.issue(IssueCode.LINE_TOTAL_MISMATCH, IssueSeverity.REVIEW, "lineItems[" + line.index() + "].lineTotal",
                            "Quantity times unit price does not equal the amount printed on line " + (line.index() + 1) + ".");
                }
            }
        }

        boolean allLinesKnown = !lines.isEmpty() && lines.stream().allMatch(l -> l.lineTotal().amount() != null);
        if (!allLinesKnown) {
            return null;
        }
        BigDecimal sum = lines.stream().map(l -> l.lineTotal().amount()).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (subtotal.amount() != null && !within(sum, subtotal.amount())) {
            run.issue(IssueCode.SUBTOTAL_MISMATCH, IssueSeverity.REVIEW, "subtotal", "The line amounts do not add up to the printed subtotal.");
        }
        BigDecimal base = subtotal.amount() != null ? subtotal.amount() : sum;
        BigDecimal discount = discountTotal.amount() == null ? BigDecimal.ZERO : discountTotal.amount().abs();
        BigDecimal afterDiscount = base.subtract(discount);
        BigDecimal expected = taxTotal.amount() == null ? afterDiscount : afterDiscount.add(taxTotal.amount());
        String difference = null;
        if (grandTotal.amount() != null) {
            boolean matches = within(expected, grandTotal.amount()) || within(afterDiscount, grandTotal.amount());
            difference = grandTotal.amount().subtract(expected).abs().toPlainString();
            if (!matches) {
                run.issue(IssueCode.GRAND_TOTAL_MISMATCH, IssueSeverity.REVIEW, "grandTotal",
                        "The printed grand total differs from the total recalculated from the line items, tax and discount.");
            }
        }
        return new ComputedTotals(sum.toPlainString(), expected.toPlainString(), difference, tolerance.toPlainString());
    }

    private void checkAgainstClaim(Run run, ClaimFacts claim, Built garage, Built vehicle, Built currency, Built documentDate, Built incidentDate) {
        if (claim == null) {
            return;
        }
        boolean garageMatches = false;
        boolean vehicleMatches = false;
        if (garage.known() && claim.garageId() != null) {
            garageMatches = sameIdentifier(garage.text(), claim.garageId());
            if (!garageMatches) {
                run.issue(IssueCode.IDENTIFIER_MISMATCH, IssueSeverity.REVIEW, "garageIdentifier", "The garage identifier on the document does not match the claim record.");
            }
        }
        if (vehicle.known() && claim.vehicleToken() != null) {
            vehicleMatches = sameIdentifier(vehicle.text(), claim.vehicleToken());
            if (!vehicleMatches) {
                run.issue(IssueCode.IDENTIFIER_MISMATCH, IssueSeverity.REVIEW, "vehicleIdentifier", "The vehicle identifier on the document does not match the claim record.");
            }
        }
        if (!garageMatches && !vehicleMatches && !garage.known() && !vehicle.known()) {
            run.issue(IssueCode.NO_IDENTIFIER_TO_CROSS_CHECK, IssueSeverity.REVIEW, null,
                    "The document shows neither a garage nor a vehicle identifier, so it could not be matched to the claim record.");
        }
        if (currency.text() != null && claim.currency() != null && !currency.text().equals(claim.currency())) {
            run.issue(IssueCode.CURRENCY_MISMATCH, IssueSeverity.REVIEW, "currency", "The currency on the document differs from the claim record.");
        }
        if (claim.incidentAt() != null) {
            LocalDate claimIncidentDate = claim.incidentAt().atZone(zone).toLocalDate();
            if (documentDate.date() != null && documentDate.date().isBefore(claimIncidentDate)) {
                run.issue(IssueCode.DOCUMENT_DATE_BEFORE_INCIDENT, IssueSeverity.REVIEW, "documentDate", "The document is dated before the incident date on the claim record.");
            }
            if (incidentDate.date() != null && !incidentDate.date().equals(claimIncidentDate)) {
                run.issue(IssueCode.INCIDENT_DATE_MISMATCH, IssueSeverity.REVIEW, "incidentDate", "The incident date on the document differs from the claim record.");
            }
        }
    }

    private static void checkRepairDates(Run run, Built start, Built end) {
        if (start.date() != null && end.date() != null && end.date().isBefore(start.date())) {
            run.issue(IssueCode.REPAIR_DATES_OUT_OF_ORDER, IssueSeverity.REVIEW, "repairCompletionDate", "The repair completion date is before the repair start date.");
        }
    }

    private static boolean sameIdentifier(String a, String b) {
        return TextCleaner.compact(a).equals(TextCleaner.compact(b));
    }

    private boolean within(BigDecimal a, BigDecimal b) {
        return a.subtract(b).abs().compareTo(tolerance) <= 0;
    }

    // ------------------------------------------------------------ one pass over one model answer

    private final class Run {
        private final UUID documentId;
        private final int pageCount;
        private final List<ReviewIssue> issues = new ArrayList<>();

        Run(UUID documentId, int pageCount) {
            this.documentId = documentId;
            this.pageCount = pageCount;
        }

        void issue(IssueCode code, IssueSeverity severity, String field, String message) {
            if (issues.size() < MAX_ISSUES) {
                issues.add(new ReviewIssue(code, severity, field, message));
            }
        }

        void requireKnown(Built built, String label) {
            if (!built.known()) {
                issue(IssueCode.REQUIRED_FIELD_UNKNOWN, IssueSeverity.REVIEW, built.result().name(), "The " + label + " could not be read from the document.");
            }
        }

        /** The row's page and quote are checked once and shared by its cells, so one bad citation is one issue, not six. */
        Line line(int index, ModelLineItem item) {
            String prefix = "lineItems[" + index + "].";
            if (item == null) {
                return new Line(index, field(prefix + "description", null, Kind.TEXT, false), field(prefix + "category", null, Kind.CATEGORY, false),
                        field(prefix + "quantity", null, Kind.QUANTITY, false), field(prefix + "unitPrice", null, Kind.MONEY, false),
                        field(prefix + "tax", null, Kind.MONEY, false), field(prefix + "lineTotal", null, Kind.MONEY, true));
            }
            Integer page = validPage(item.page(), "lineItems[" + index + "]");
            String quote = item.quote();
            return new Line(index,
                    field(prefix + "description", cell(item.description(), page, quote), Kind.TEXT, false),
                    field(prefix + "category", cell(item.category(), page, quote), Kind.CATEGORY, false),
                    field(prefix + "quantity", cell(item.quantity(), page, quote), Kind.QUANTITY, false),
                    field(prefix + "unitPrice", cell(item.unitPrice(), page, quote), Kind.MONEY, false),
                    field(prefix + "tax", cell(item.tax(), page, quote), Kind.MONEY, false),
                    field(prefix + "lineTotal", cell(item.lineTotal(), page, quote), Kind.MONEY, true));
        }

        private ModelField cell(String value, Integer page, String quote) {
            return value == null || value.isBlank() ? new ModelField(null, null, null, "NOT_PRESENT") : new ModelField(value, page, quote, null);
        }

        List<ReportedWarning> reported(List<ModelWarning> warnings) {
            if (warnings == null) {
                return List.of();
            }
            List<ReportedWarning> result = new ArrayList<>();
            for (ModelWarning warning : warnings) {
                if (warning == null) {
                    continue;
                }
                String code = warning.code() == null ? "OTHER_PROBLEM" : warning.code().strip().toUpperCase(Locale.ROOT);
                if (!REPORTED_CODES.contains(code)) {
                    code = "OTHER_PROBLEM";
                }
                Integer page = validPage(warning.page(), "warnings");
                String quote = TextCleaner.clean(warning.quote(), QUOTE_MAX);
                result.add(new ReportedWarning(code, page, quote));
                issue(IssueCode.MODEL_REPORTED_PROBLEM, IssueSeverity.REVIEW, null,
                        "While reading the document the model reported a problem: " + code + (page == null ? "." : " (page " + page + ")."));
                if (result.size() >= 20) {
                    break;
                }
            }
            return List.copyOf(result);
        }

        private Integer validPage(Integer page, String field) {
            if (page == null) {
                return null;
            }
            if (page < 1 || page > pageCount) {
                issue(IssueCode.PAGE_OUT_OF_RANGE, IssueSeverity.REVIEW, field, "A cited page number is outside the document (it has " + pageCount + " page" + (pageCount == 1 ? "" : "s") + ").");
                return null;
            }
            return page;
        }

        /** @param quoteRequired an extracted value without a source quote is an ungrounded value and needs review */
        Built field(String name, ModelField raw, Kind kind, boolean quoteRequired) {
            if (raw == null) {
                return unknown(name, "NOT_RETURNED");
            }
            String text = TextCleaner.clean(raw.value(), maxLength(kind));
            if (text == null) {
                return unknown(name, reason(raw.unknownReason()));
            }

            String normalized = null;
            String note = null;
            BigDecimal amount = null;
            LocalDate date = null;
            switch (kind) {
                case MONEY -> {
                    AmountParser.Parsed parsed = AmountParser.parseMoney(text);
                    amount = parsed.value();
                    normalized = amount == null ? null : amount.toPlainString();
                    note = parsed.note();
                }
                case QUANTITY -> {
                    AmountParser.Parsed parsed = AmountParser.parseQuantity(text);
                    amount = parsed.value();
                    normalized = amount == null ? null : amount.toPlainString();
                    note = parsed.note();
                }
                case DATE -> {
                    DateParser.Parsed parsed = DateParser.parse(text);
                    date = parsed.value();
                    normalized = date == null ? null : date.toString();
                    note = parsed.note();
                }
                case CURRENCY -> {
                    CurrencyNormalizer.Parsed parsed = CurrencyNormalizer.parse(text);
                    normalized = parsed.code();
                    note = parsed.note();
                }
                case CATEGORY -> normalized = category(text);
                case IDENTIFIER, TEXT -> normalized = text;
            }
            if (normalized == null) {
                issue(IssueCode.VALUE_NOT_PARSEABLE, IssueSeverity.REVIEW, name, "The value of " + name + " could not be read as a " + describe(kind) + ".");
            } else if (note != null && kind == Kind.DATE && (note.contains("DAY_FIRST_ASSUMED") || note.contains("TWO_DIGIT_YEAR"))) {
                issue(IssueCode.DATE_FORMAT_ASSUMED, IssueSeverity.INFO, name, "The date format was ambiguous and was read as " + normalized + " (day first).");
            } else if (note != null && (kind == Kind.MONEY || kind == Kind.QUANTITY)) {
                // A decimal comma or dotted thousands is unusual on Indian documents; reading it is an assumption a person should confirm.
                issue(IssueCode.AMOUNT_FORMAT_ASSUMED, IssueSeverity.REVIEW, name, "The number format of " + name + " was unusual and was read as " + normalized + ".");
            }

            Integer page = validPage(raw.page(), name);
            String quote = TextCleaner.clean(raw.quote(), QUOTE_MAX);
            if (quoteRequired && quote == null) {
                issue(IssueCode.NO_SOURCE_QUOTE, IssueSeverity.REVIEW, name, "No source text was given for " + name + ", so the value cannot be traced to the document.");
            }
            FieldResult result = new FieldResult(name, FieldState.EXTRACTED, text, normalized, note, null, new Source(documentId, page, quote));
            String comparable = kind == Kind.CURRENCY || kind == Kind.IDENTIFIER ? normalized : null;
            return new Built(result, amount, date, comparable);
        }

        private Built unknown(String name, String reason) {
            return new Built(new FieldResult(name, FieldState.UNKNOWN, null, null, null, reason, null), null, null, null);
        }
    }

    private static String reason(String modelReason) {
        String cleaned = TextCleaner.clean(modelReason, 80);
        return cleaned == null ? "UNSPECIFIED" : cleaned;
    }

    private static int maxLength(Kind kind) {
        return switch (kind) {
            case TEXT -> 1000;
            case IDENTIFIER -> 100;
            case DATE, MONEY, QUANTITY, CATEGORY -> 40;
            case CURRENCY -> 20;
        };
    }

    private static String describe(Kind kind) {
        return switch (kind) {
            case DATE -> "date";
            case MONEY -> "money amount";
            case QUANTITY -> "quantity";
            case CURRENCY -> "currency";
            default -> "value";
        };
    }

    private static String category(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("part") || lower.contains("spare") || lower.contains("material")) {
            return "PART";
        }
        if (lower.contains("labo")) {
            return "LABOUR";
        }
        if (lower.contains("paint")) {
            return "PAINT";
        }
        return "OTHER";
    }
}
