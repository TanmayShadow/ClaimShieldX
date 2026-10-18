package com.example.ClaimShieldX.rules.engine;

import com.example.ClaimShieldX.documents.model.DocumentType;
import com.example.ClaimShieldX.rules.config.RuleProperties;
import com.example.ClaimShieldX.rules.engine.RuleInputs.DocumentFacts;
import com.example.ClaimShieldX.rules.model.EvidenceRef;
import com.example.ClaimShieldX.rules.model.InputReliability;
import com.example.ClaimShieldX.rules.model.RuleDefinition;
import com.example.ClaimShieldX.rules.model.RuleResult;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Compares the final invoice total with the estimate. Three baselines are computed when available: the garage estimate
 * document, the claim record's initial estimate and its approved estimate. Only ONE decides the outcome, in that order of
 * preference, so the same underlying increase is never counted twice; the others are listed for the reviewer. An invoice
 * is flagged only when it is above the baseline by more than the percentage threshold AND by at least the minimum amount.
 * A decrease is never flagged. The percentage is rounded to two decimals before it is compared.
 */
public final class EstimateInvoiceVarianceRule implements Rule {
    public static final RuleDefinition DEFINITION = new RuleDefinition("ESTIMATE_INVOICE_VARIANCE", "1", "Estimate-to-invoice variance",
            "Compares the final invoice total with the garage estimate (or, when there is none, the estimate amount on the claim) and reports the absolute and percentage difference.",
            List.of("A final invoice that has been read", "A baseline: a garage estimate that has been read, or an estimate amount on the claim"),
            List.of("Supplementary damage or repairs found after the estimate was made, or a revised estimate.", "Hidden damage found when the vehicle was dismantled, or parts availability.",
                    "Tax or labour-rate changes between the estimate and the invoice.", "A variance is not, by itself, a finding about the claim."));

    private record Baseline(String basis, BigDecimal amount, String currency, List<String> doubts, EvidenceRef evidence) {
    }

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
                    : Results.incomplete(DEFINITION, "No final invoice has been received, so it could not be compared with the estimate.", List.of("FINAL_INVOICE"), Results.values(), List.of());
        }
        Extracted x = Extracted.of(invoice);
        if (x == null) {
            return Results.incomplete(DEFINITION, "The final invoice has not been read successfully, so it could not be compared with the estimate.",
                    List.of("FINAL_INVOICE_EXTRACTION"), Map.of("invoiceDocumentId", invoice.id().toString()), List.of(EvidenceRef.document(invoice.id(), "FINAL_INVOICE")));
        }
        BigDecimal invoiceTotal = x.amount("grandTotal");
        String invoiceCurrency = x.text("currency");
        if (invoiceTotal == null || invoiceCurrency == null) {
            return Results.incomplete(DEFINITION, "The grand total or currency of the final invoice could not be read, so it could not be compared with the estimate.",
                    List.of(invoiceTotal == null ? "INVOICE_GRAND_TOTAL" : "INVOICE_CURRENCY"), Map.of("invoiceDocumentId", invoice.id().toString()),
                    List.of(EvidenceRef.document(invoice.id(), "FINAL_INVOICE")));
        }
        List<String> invoiceDoubts = x.doubts(java.util.Set.of());

        List<Baseline> candidates = new ArrayList<>();
        List<Map<String, Object>> skipped = new ArrayList<>();
        DocumentFacts estimateDoc = Docs.newest(inputs, DocumentType.GARAGE_ESTIMATE);
        Extracted estimate = Extracted.of(estimateDoc);
        if (estimate != null && estimate.amount("grandTotal") != null) {
            candidates.add(new Baseline("GARAGE_ESTIMATE_DOCUMENT", estimate.amount("grandTotal"), estimate.text("currency"), estimate.doubts(java.util.Set.of()), estimate.ref("grandTotal")));
        } else if (estimateDoc != null) {
            skipped.add(skip("GARAGE_ESTIMATE_DOCUMENT", estimate == null ? "ESTIMATE_NOT_READ" : "ESTIMATE_TOTAL_NOT_READABLE"));
        }
        RuleInputs.ClaimFacts claim = inputs.claim();
        if (claim.initialEstimate() != null) {
            candidates.add(new Baseline("CLAIM_INITIAL_ESTIMATE", claim.initialEstimate(), claim.currency(), List.of(), EvidenceRef.claimField("financial.initialEstimateAmount")));
        }
        if (claim.approvedEstimate() != null) {
            candidates.add(new Baseline("CLAIM_APPROVED_ESTIMATE", claim.approvedEstimate(), claim.currency(), List.of(), EvidenceRef.claimField("financial.approvedEstimateAmount")));
        }

        List<Baseline> usable = new ArrayList<>();
        for (Baseline b : candidates) {
            if (!invoiceCurrency.equals(b.currency())) {
                skipped.add(skip(b.basis(), "CURRENCY_DIFFERS_OR_UNKNOWN"));
            } else {
                usable.add(b);
            }
        }
        Map<String, Object> values = Results.values();
        values.put("invoiceDocumentId", invoice.id().toString());
        values.put("invoiceTotal", Results.plain(invoiceTotal));
        values.put("currency", invoiceCurrency);
        values.put("skippedBaselines", skipped);
        values.put("percentThreshold", Results.plain(config.variancePercentThreshold()));
        values.put("minimumIncrease", Results.plain(config.varianceMinimumIncrease()));
        boolean supplementary = !Docs.current(inputs, DocumentType.SUPPLEMENTARY_ESTIMATE).isEmpty();
        values.put("supplementaryEstimateReceived", supplementary);
        if (supplementary) {
            values.put("supplementaryEstimateNote", "A supplementary estimate was received but its amount is not read by this system, so it is not part of any baseline.");
        }
        if (usable.isEmpty()) {
            return Results.incomplete(DEFINITION, "There is nothing to compare the final invoice with: no readable garage estimate and no estimate amount on the claim in the invoice's currency.",
                    List.of("GARAGE_ESTIMATE_OR_CLAIM_ESTIMATE_AMOUNT"), values, List.of(x.ref("grandTotal")));
        }

        List<Map<String, Object>> comparisons = new ArrayList<>();
        Baseline primary = usable.get(0);
        boolean primaryExceeds = false;
        BigDecimal primaryDifference = null;
        BigDecimal primaryPercent = null;
        for (Baseline b : usable) {
            BigDecimal difference = Results.money(invoiceTotal.subtract(b.amount()));
            BigDecimal percent = b.amount().signum() > 0 ? difference.multiply(BigDecimal.valueOf(100)).divide(b.amount(), 2, RoundingMode.HALF_UP) : null;
            boolean exceeds = difference.signum() > 0 && difference.compareTo(config.varianceMinimumIncrease()) >= 0
                    && (percent == null || percent.compareTo(config.variancePercentThreshold()) > 0);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("basis", b.basis());
            entry.put("baseline", Results.plain(b.amount()));
            entry.put("difference", Results.plain(difference));
            entry.put("percent", percent == null ? null : percent.toPlainString());
            entry.put("direction", difference.signum() > 0 ? "INCREASE" : difference.signum() < 0 ? "DECREASE" : "NONE");
            entry.put("exceedsThreshold", exceeds);
            entry.put("decidesOutcome", b == primary);
            comparisons.add(entry);
            if (b == primary) {
                primaryExceeds = exceeds;
                primaryDifference = difference;
                primaryPercent = percent;
            }
        }
        values.put("primaryBasis", primary.basis());
        values.put("comparisons", comparisons);

        List<EvidenceRef> evidence = new ArrayList<>(List.of(x.ref("grandTotal"), primary.evidence()));
        TreeSet<String> doubts = new TreeSet<>(invoiceDoubts);
        doubts.addAll(primary.doubts());
        List<String> doubtList = List.copyOf(doubts);
        InputReliability reliability = Extracted.reliability(doubtList);

        String against = describe(primary.basis());
        if (primaryExceeds) {
            String how = primaryPercent == null ? "" : " (" + primaryPercent.toPlainString() + "%)";
            return Results.flag(DEFINITION, Results.severity(true, doubtList), reliability, doubtList,
                    "The final invoice total of " + invoiceCurrency + " " + Results.plain(invoiceTotal) + " is " + Results.plain(primaryDifference) + how
                            + " above " + against + " of " + Results.plain(primary.amount()) + ", beyond the configured threshold of more than "
                            + Results.plain(config.variancePercentThreshold()) + "% and at least " + Results.plain(config.varianceMinimumIncrease()) + "."
                            + (supplementary ? " A supplementary estimate was received and may explain part of the increase." : ""),
                    values, evidence);
        }
        String direction = primaryDifference.signum() > 0 ? Results.plain(primaryDifference) + " above" : primaryDifference.signum() < 0 ? Results.plain(primaryDifference.negate()) + " below" : "equal to";
        return Results.pass(DEFINITION, reliability, doubtList,
                "The final invoice total of " + invoiceCurrency + " " + Results.plain(invoiceTotal) + " is " + direction + " " + against + " of " + Results.plain(primary.amount())
                        + ", which is within the configured threshold.", values, evidence);
    }

    private static Map<String, Object> skip(String basis, String reason) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("basis", basis);
        map.put("reason", reason);
        return map;
    }

    private static String describe(String basis) {
        return switch (basis) {
            case "GARAGE_ESTIMATE_DOCUMENT" -> "the garage estimate";
            case "CLAIM_INITIAL_ESTIMATE" -> "the initial estimate on the claim";
            default -> "the approved estimate on the claim";
        };
    }
}
