package com.example.ClaimShieldX.rules.engine;

import com.example.ClaimShieldX.documents.model.DocumentType;
import com.example.ClaimShieldX.extraction.schema.IssueCode;
import com.example.ClaimShieldX.rules.config.RuleProperties;
import com.example.ClaimShieldX.rules.engine.Extracted.Line;
import com.example.ClaimShieldX.rules.engine.RuleInputs.DocumentFacts;
import com.example.ClaimShieldX.rules.model.EvidenceRef;
import com.example.ClaimShieldX.rules.model.InputReliability;
import com.example.ClaimShieldX.rules.model.RuleDefinition;
import com.example.ClaimShieldX.rules.model.RuleResult;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Recalculates the final invoice from its own extracted figures. Policy, all in BigDecimal and at two decimals:
 * a line may differ from quantity x unit price by the base allowance plus 0.005 per unit (a unit price printed to the
 * paisa can be off by half a paisa each); the subtotal and grand total may differ by the round-off allowance. The grand
 * total may be subtotal - discount + tax, or subtotal - discount when the tax is already inside the line amounts.
 */
public final class InvoiceArithmeticRule implements Rule {
    public static final RuleDefinition DEFINITION = new RuleDefinition("INVOICE_ARITHMETIC", "1", "Invoice arithmetic",
            "Recalculates the final invoice's lines, subtotal, discount, tax and grand total from the extracted figures and reports where they do not reconcile after rounding.",
            List.of("A final invoice that has been read (extraction)"),
            List.of("Tax, a discount or a round-off line that is printed differently from how it was read.", "A figure misread from the document (check the source text shown).",
                    "A rounding convention of the garage that differs from the configured allowance."));

    private static final Set<IssueCode> OWN_FINDINGS = Set.of(IssueCode.LINE_TOTAL_MISMATCH, IssueCode.SUBTOTAL_MISMATCH, IssueCode.GRAND_TOTAL_MISMATCH);
    private static final BigDecimal HALF_A_PAISA = new BigDecimal("0.005");

    @Override
    public RuleDefinition definition() {
        return DEFINITION;
    }

    @Override
    public RuleResult evaluate(RuleInputs inputs, RuleProperties config) {
        DocumentFacts invoice = Docs.newest(inputs, DocumentType.FINAL_INVOICE);
        if (invoice == null) {
            return Docs.notApplicable(inputs, DocumentType.FINAL_INVOICE)
                    ? Results.notApplicable(DEFINITION, "The checklist for this claim does not expect a final invoice at its stage.", Results.values())
                    : Results.incomplete(DEFINITION, "No final invoice has been received, so its arithmetic could not be checked.", List.of("FINAL_INVOICE"), Results.values(), List.of());
        }
        Extracted x = Extracted.of(invoice);
        if (x == null) {
            return Results.incomplete(DEFINITION, "The final invoice has not been read successfully, so its arithmetic could not be checked.",
                    List.of("FINAL_INVOICE_EXTRACTION"), Map.of("invoiceDocumentId", invoice.id().toString()), List.of(EvidenceRef.document(invoice.id(), "FINAL_INVOICE")));
        }

        List<Map<String, Object>> mismatches = new ArrayList<>();
        List<String> summaries = new ArrayList<>();
        List<EvidenceRef> evidence = new ArrayList<>();
        List<String> notChecked = new ArrayList<>();
        int linesChecked = 0;
        int linesNotChecked = 0;
        BigDecimal lineSum = BigDecimal.ZERO;
        boolean allLineTotalsKnown = !x.lines().isEmpty();

        for (Line line : x.lines()) {
            BigDecimal total = line.lineTotalValue();
            if (total == null) {
                allLineTotalsKnown = false;
            } else {
                lineSum = lineSum.add(total);
            }
            BigDecimal quantity = line.quantityValue();
            BigDecimal unitPrice = line.unitPriceValue();
            if (quantity == null || unitPrice == null || total == null) {
                linesNotChecked++;
                continue;
            }
            linesChecked++;
            BigDecimal expected = Results.money(quantity.multiply(unitPrice));
            BigDecimal allowance = config.lineBaseAllowance().add(HALF_A_PAISA.multiply(quantity.abs()));
            BigDecimal tax = line.taxValue();
            boolean reconciles = within(total, expected, allowance) || (tax != null && within(total, expected.add(tax), allowance));
            if (!reconciles) {
                String summary = "Line " + (line.index() + 1) + ": quantity " + quantity.stripTrailingZeros().toPlainString() + " x " + Results.plain(unitPrice)
                        + " = " + Results.plain(expected) + ", but " + Results.plain(total) + " is printed.";
                mismatches.add(mismatch("LINE_TOTAL", summary, "line", line.index() + 1, "expected", Results.plain(expected), "printed", Results.plain(total),
                        "difference", Results.plain(total.subtract(expected))));
                summaries.add(summary);
                evidence.add(x.lineRef(line));
            }
        }
        if (linesNotChecked > 0) {
            notChecked.add(linesNotChecked + " line(s) lacked a readable quantity, unit price or amount");
        }

        BigDecimal subtotal = x.amount("subtotal");
        boolean subtotalChecked = false;
        if (subtotal != null && allLineTotalsKnown) {
            subtotalChecked = true;
            if (!within(lineSum, subtotal, config.roundOffAllowance())) {
                String summary = "The line amounts add up to " + Results.plain(lineSum) + " but the printed subtotal is " + Results.plain(subtotal) + ".";
                mismatches.add(mismatch("SUBTOTAL", summary, "expected", Results.plain(lineSum), "printed", Results.plain(subtotal), "difference", Results.plain(subtotal.subtract(lineSum))));
                summaries.add(summary);
                evidence.add(x.ref("subtotal"));
            }
        } else if (subtotal != null) {
            notChecked.add("the subtotal could not be compared because not every line amount was readable");
        }

        BigDecimal grandTotal = x.amount("grandTotal");
        BigDecimal base = subtotal != null ? subtotal : (allLineTotalsKnown ? lineSum : null);
        BigDecimal discount = x.amount("discountTotal");
        BigDecimal tax = x.amount("taxTotal");
        boolean discountUnreadable = discount == null && !x.printedAsAbsent("discountTotal");
        boolean taxUnreadable = tax == null && !x.printedAsAbsent("taxTotal");
        boolean grandTotalChecked = false;
        String matchedFormula = null;
        if (grandTotal == null) {
            notChecked.add("the grand total was not readable");
        } else if (base == null) {
            notChecked.add("the grand total could not be recalculated because neither a subtotal nor every line amount was readable");
        } else if (discountUnreadable) {
            notChecked.add("the discount was unreadable, so the grand total could not be recalculated");
        } else {
            BigDecimal afterDiscount = base.subtract(discount == null ? BigDecimal.ZERO : discount.abs());
            BigDecimal taxIncluded = afterDiscount;
            if (within(grandTotal, taxIncluded, config.roundOffAllowance())) {
                grandTotalChecked = true;
                matchedFormula = "TAX_ALREADY_IN_LINES";
            } else if (taxUnreadable) {
                notChecked.add("the tax total was unreadable, so the grand total could not be fully recalculated");
            } else {
                grandTotalChecked = true;
                BigDecimal taxAdded = afterDiscount.add(tax == null ? BigDecimal.ZERO : tax);
                if (within(grandTotal, taxAdded, config.roundOffAllowance())) {
                    matchedFormula = "TAX_ADDED";
                } else {
                    String summary = "The printed grand total is " + Results.plain(grandTotal) + " but the figures give " + Results.plain(taxAdded)
                            + (tax == null || tax.signum() == 0 ? "" : " (or " + Results.plain(taxIncluded) + " if the tax is already in the lines)") + ".";
                    mismatches.add(mismatch("GRAND_TOTAL", summary, "expected", Results.plain(taxAdded), "expectedIfTaxInLines", Results.plain(taxIncluded),
                            "printed", Results.plain(grandTotal), "difference", Results.plain(grandTotal.subtract(taxAdded))));
                    summaries.add(summary);
                    evidence.add(x.ref("grandTotal"));
                }
            }
        }

        int checksRun = linesChecked + (subtotalChecked ? 1 : 0) + (grandTotalChecked ? 1 : 0);
        List<String> doubts = x.doubts(OWN_FINDINGS);
        InputReliability reliability = Extracted.reliability(doubts);
        Map<String, Object> values = Results.values();
        values.put("invoiceDocumentId", invoice.id().toString());
        values.put("currency", x.text("currency"));
        values.put("checksRun", checksRun);
        values.put("linesChecked", linesChecked);
        values.put("subtotalChecked", subtotalChecked);
        values.put("grandTotalChecked", grandTotalChecked);
        if (allLineTotalsKnown) {
            values.put("lineAmountsSum", Results.plain(lineSum));
        }
        if (subtotal != null) {
            values.put("printedSubtotal", Results.plain(subtotal));
        }
        if (grandTotal != null) {
            values.put("printedGrandTotal", Results.plain(grandTotal));
        }
        if (matchedFormula != null) {
            values.put("grandTotalFormula", matchedFormula);
        }
        values.put("roundOffAllowance", Results.plain(config.roundOffAllowance()));
        values.put("mismatches", mismatches);
        values.put("notChecked", notChecked);

        if (!mismatches.isEmpty()) {
            String shown = String.join(" ", summaries.subList(0, Math.min(3, summaries.size())));
            String more = summaries.size() > 3 ? " (" + (summaries.size() - 3) + " more)" : "";
            return Results.flag(DEFINITION, Results.severity(true, doubts), reliability, doubts,
                    (mismatches.size() == 1 ? "1 place on the final invoice does not reconcile" : mismatches.size() + " places on the final invoice do not reconcile") + " after rounding. " + shown + more, values, evidence);
        }
        if (checksRun == 0) {
            return Results.incomplete(DEFINITION, "Nothing on the final invoice could be recalculated: " + String.join("; ", notChecked) + ".",
                    List.of("READABLE_QUANTITIES_PRICES_OR_TOTALS"), values, List.of(EvidenceRef.document(invoice.id(), "FINAL_INVOICE")));
        }
        return Results.pass(DEFINITION, reliability, doubts, checksRun + " arithmetic check" + (checksRun == 1 ? "" : "s")
                + " on the final invoice reconcile within the rounding allowance." + (notChecked.isEmpty() ? "" : " Not checked: " + String.join("; ", notChecked) + "."),
                values, List.of(EvidenceRef.document(invoice.id(), "FINAL_INVOICE")));
    }

    private static boolean within(BigDecimal a, BigDecimal b, BigDecimal allowance) {
        return a.subtract(b).abs().compareTo(allowance) <= 0;
    }

    private static Map<String, Object> mismatch(String check, String summary, Object... pairs) {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("check", check);
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        map.put("summary", summary);
        return map;
    }
}
