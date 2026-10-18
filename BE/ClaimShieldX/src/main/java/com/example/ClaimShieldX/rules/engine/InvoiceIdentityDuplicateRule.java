package com.example.ClaimShieldX.rules.engine;

import com.example.ClaimShieldX.common.utils.TextCleaner;
import com.example.ClaimShieldX.documents.model.DocumentType;
import com.example.ClaimShieldX.rules.config.RuleProperties;
import com.example.ClaimShieldX.rules.engine.RuleInputs.DocumentFacts;
import com.example.ClaimShieldX.rules.engine.RuleInputs.InvoiceIdentity;
import com.example.ClaimShieldX.rules.model.EvidenceRef;
import com.example.ClaimShieldX.rules.model.RuleDefinition;
import com.example.ClaimShieldX.rules.model.RuleResult;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Looks for another invoice with the same number from the same issuer. The invoice number alone is not enough (two garages can
 * both print "INV-001"), so the issuer must also match; the amount and date then say how close the repeat is. A same-number,
 * same-issuer invoice with a different amount is recorded as information because a corrected or reissued invoice looks exactly
 * like that. An earlier version that was replaced on this same claim is the expected reissue and is not a flag.
 */
public final class InvoiceIdentityDuplicateRule implements Rule {
    public static final RuleDefinition DEFINITION = new RuleDefinition("INVOICE_IDENTITY_DUPLICATE", "1", "Invoice identity duplicate",
            "Looks for another final invoice with the same invoice number from the same issuer (garage) and compares amount and date, on this claim and on other claims in the organization.",
            List.of("A final invoice that has been read, with a readable invoice number and issuer"),
            List.of("A reissued or corrected invoice (same number, different amount or date).", "An issuer that restarts its numbering, or two branches sharing a garage identifier.",
                    "The same invoice legitimately submitted again after the first claim was withdrawn or returned."));

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
                    : Results.incomplete(DEFINITION, "No final invoice has been received, so its identity could not be compared with other invoices.", List.of("FINAL_INVOICE"), Results.values(), List.of());
        }
        Extracted x = Extracted.of(invoice);
        if (x == null) {
            return Results.incomplete(DEFINITION, "The final invoice has not been read successfully, so its identity could not be compared with other invoices.",
                    List.of("FINAL_INVOICE_EXTRACTION"), Map.of("invoiceDocumentId", invoice.id().toString()), List.of(EvidenceRef.document(invoice.id(), "FINAL_INVOICE")));
        }
        String number = x.text("documentNumber");
        String key = InvoiceKey.of(number);
        String issuer = x.text("garageIdentifier");
        String claimGarage = inputs.claim().garageId();
        if (key == null || (issuer == null && claimGarage == null)) {
            return Results.incomplete(DEFINITION, "The invoice number could not be read, or the issuer is unknown (the invoice prints no garage identifier and the claim records no garage), so the invoice could not be compared with others.",
                    List.of(key == null ? "INVOICE_NUMBER" : "INVOICE_ISSUER"), Map.of("invoiceDocumentId", invoice.id().toString()), List.of(x.ref("documentNumber"), x.ref("garageIdentifier")));
        }
        BigDecimal total = x.amount("grandTotal");
        String currency = x.text("currency");
        java.time.LocalDate date = x.date("documentDate");
        List<String> doubts = x.doubts(Set.of());

        List<Map<String, Object>> matches = new ArrayList<>();
        List<EvidenceRef> evidence = new ArrayList<>(List.of(x.ref("documentNumber"), x.ref("garageIdentifier")));
        int otherIssuer = 0;
        boolean material = false;
        List<InvoiceIdentity> ordered = inputs.invoiceCandidates().stream()
                .sorted(Comparator.comparing(InvoiceIdentity::receivedAt).thenComparing(InvoiceIdentity::documentId)).toList();
        for (InvoiceIdentity c : ordered) {
            if (c.documentId().equals(invoice.id()) || !key.equals(InvoiceKey.of(c.invoiceNumber()))) {
                continue;
            }
            String issuerBasis = sameIssuer(issuer, claimGarage, c);
            if (issuerBasis == null) {
                otherIssuer++;
                continue;
            }
            boolean sameAmount = total != null && c.grandTotal() != null && currency != null && currency.equals(c.currency())
                    && Results.money(total).compareTo(Results.money(c.grandTotal())) == 0;
            boolean sameDate = date != null && c.documentDate() != null && date.toString().equals(c.documentDate());
            boolean sameClaim = c.claimId().equals(inputs.claim().id());
            String level = sameAmount ? (sameDate ? "SAME_NUMBER_ISSUER_AMOUNT_AND_DATE" : "SAME_NUMBER_ISSUER_AND_AMOUNT") : "SAME_NUMBER_ISSUER_DIFFERENT_AMOUNT";
            boolean expectedReissue = sameClaim && c.superseded();
            boolean needsReview = !expectedReissue && sameAmount;
            material |= needsReview;
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("level", level);
            entry.put("issuerBasis", issuerBasis);
            entry.put("location", sameClaim ? (expectedReissue ? "EARLIER_VERSION_ON_THIS_CLAIM" : "ANOTHER_INVOICE_ON_THIS_CLAIM") : "ANOTHER_CLAIM");
            entry.put("claimInternalId", c.claimInternalId());
            entry.put("documentId", c.documentId().toString());
            entry.put("documentReplaced", c.superseded());
            entry.put("amountDifference", total == null || c.grandTotal() == null || !sameCurrency(currency, c.currency()) ? null : Results.plain(total.subtract(c.grandTotal())));
            entry.put("otherAmount", c.grandTotal() == null ? null : Results.plain(c.grandTotal()));
            entry.put("otherDate", c.documentDate());
            if (!sameClaim) {
                entry.put("sameVehicleToken", c.sameVehicleToken());
                entry.put("samePolicyToken", c.samePolicyToken());
            }
            matches.add(entry);
            evidence.add(EvidenceRef.otherClaimField(c.claimId(), c.documentId(), c.extractionId(), "documentNumber", null, null));
        }

        Map<String, Object> values = Results.values();
        values.put("invoiceDocumentId", invoice.id().toString());
        values.put("invoiceNumber", number);
        values.put("issuer", issuer);
        values.put("claimGarage", claimGarage);
        values.put("grandTotal", total == null ? null : Results.plain(total));
        values.put("documentDate", date == null ? null : date.toString());
        values.put("matches", matches);
        values.put("sameNumberFromOtherIssuers", otherIssuer);
        values.put("comparedWith", "invoice number (letters and digits only) and issuer (the garage identifier printed on the invoice, else the garage recorded on the claim); then amount and date");
        if (matches.isEmpty()) {
            return Results.pass(DEFINITION, Extracted.reliability(doubts), doubts,
                    "No other final invoice with the same number from the same issuer was found in the organization.", values, evidence);
        }
        long unexplained = matches.stream().filter(m -> !"EARLIER_VERSION_ON_THIS_CLAIM".equals(m.get("location"))).count();
        if (unexplained == 0) {
            return Results.flag(DEFINITION, Results.severity(false), Extracted.reliability(doubts), doubts,
                    "Only an earlier version of this invoice, replaced on this claim, shares its number and issuer. That is the expected result of a reissue.", values, evidence);
        }
        return Results.flag(DEFINITION, Results.severity(material, doubts), Extracted.reliability(doubts), doubts,
                matches.size() + " other invoice" + (matches.size() == 1 ? "" : "s") + " with the same number from the same issuer: "
                        + describe(matches) + " This is a signal to check, not proof of duplication.", values, evidence);
    }

    /**
     * Whether the two invoices have the same issuer, and on what basis. Identifiers printed on both invoices decide when both exist (a
     * difference means a different issuer, whatever the claims say). Otherwise the garage recorded on each claim is the issuer, which is
     * what a repair invoice is about; that basis is reported because the invoice itself was not cross-checked. Null means not the same, or not comparable.
     */
    static String sameIssuer(String issuer, String claimGarage, InvoiceIdentity other) {
        if (issuer != null && other.garageIdentifier() != null) {
            return TextCleaner.compact(issuer).equals(TextCleaner.compact(other.garageIdentifier())) ? "DOCUMENT_IDENTIFIER" : null;
        }
        if (claimGarage != null && other.claimGarageId() != null && TextCleaner.compact(claimGarage).equals(TextCleaner.compact(other.claimGarageId()))) {
            return "CLAIM_RECORD_GARAGE";
        }
        return null;
    }

    private static boolean sameCurrency(String a, String b) {
        return a != null && a.equals(b);
    }

    private static String describe(List<Map<String, Object>> matches) {
        long same = matches.stream().filter(m -> !"SAME_NUMBER_ISSUER_DIFFERENT_AMOUNT".equals(m.get("level"))).count();
        long different = matches.size() - same;
        return same + " with the same amount" + (different > 0 ? " and " + different + " with a different amount (possibly a corrected invoice)." : ".");
    }
}
