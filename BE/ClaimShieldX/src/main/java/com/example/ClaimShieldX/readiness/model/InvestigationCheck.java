package com.example.ClaimShieldX.readiness.model;

import com.example.ClaimShieldX.documents.model.DocumentType;

import java.util.List;

import static com.example.ClaimShieldX.documents.model.DocumentType.*;

/**
 * The checks that need specific documents, so a missing document can be reported as "this check could not run" and never as a
 * finding. These ids are the ids of the deterministic rules built in B7, except REPAIR_SCOPE_REVIEW, which is the model-assisted
 * review of B10 and has no rule yet. A check lists only the documents it cannot run without: the date check works from the claim
 * record alone (documents add comparisons), and the variance check can fall back to the estimate amount on the claim.
 * Checks that need no document of this claim (exact file duplicates, history) are not listed.
 */
public enum InvestigationCheck {
    INVOICE_ARITHMETIC("Invoice arithmetic check", FINAL_INVOICE),
    ESTIMATE_INVOICE_VARIANCE("Estimate-to-invoice variance check", FINAL_INVOICE),
    DATE_CONSISTENCY("Date consistency check"),
    INVOICE_IDENTITY_DUPLICATE("Invoice identity duplicate check", FINAL_INVOICE),
    REPEATED_LINE_ITEMS("Repeated line items check", FINAL_INVOICE),
    REPAIR_SCOPE_REVIEW("Repair scope review", DAMAGE_PHOTOGRAPH, FINAL_INVOICE);
    private final String label;
    private final List<DocumentType> documents;

    InvestigationCheck(String label, DocumentType... documents) {
        this.label = label;
        this.documents = List.of(documents);
    }

    public String label() {
        return label;
    }

    public List<DocumentType> documents() {
        return documents;
    }
}
