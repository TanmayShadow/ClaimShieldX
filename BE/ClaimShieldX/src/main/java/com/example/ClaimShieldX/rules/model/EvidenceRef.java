package com.example.ClaimShieldX.rules.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

/**
 * Points at the source of a value a rule used. These are descriptive references; the stable, snapshot-scoped reference
 * format is decided in B9. {@code quote} is the model's reading of the text near an extracted value, so it is document
 * text and must be shown as plain text.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EvidenceRef(Kind kind, UUID claimId, UUID documentId, UUID extractionId, String field, Integer page, String quote,
                          String claimField, UUID importId, Integer rowNumber, String sourceRecordId, String documentType, String note) {

    public enum Kind {
        DOCUMENT_FIELD,
        DOCUMENT,
        OTHER_CLAIM_DOCUMENT,
        CLAIM_FIELD,
        HISTORICAL_RECORD,
        CHECKLIST_ITEM
    }

    public static EvidenceRef documentField(UUID documentId, UUID extractionId, String field, Integer page, String quote) {
        return new EvidenceRef(Kind.DOCUMENT_FIELD, null, documentId, extractionId, field, page, quote, null, null, null, null, null, null);
    }

    public static EvidenceRef document(UUID documentId, String documentType) {
        return new EvidenceRef(Kind.DOCUMENT, null, documentId, null, null, null, null, null, null, null, null, documentType, null);
    }

    public static EvidenceRef otherClaimDocument(UUID claimId, UUID documentId, String documentType, String note) {
        return new EvidenceRef(Kind.OTHER_CLAIM_DOCUMENT, claimId, documentId, null, null, null, null, null, null, null, null, documentType, note);
    }

    public static EvidenceRef otherClaimField(UUID claimId, UUID documentId, UUID extractionId, String field, Integer page, String quote) {
        return new EvidenceRef(Kind.OTHER_CLAIM_DOCUMENT, claimId, documentId, extractionId, field, page, quote, null, null, null, null, null, null);
    }

    public static EvidenceRef claimField(String claimField) {
        return new EvidenceRef(Kind.CLAIM_FIELD, null, null, null, null, null, null, claimField, null, null, null, null, null);
    }

    public static EvidenceRef historicalRecord(UUID importId, Integer rowNumber, String sourceRecordId) {
        return new EvidenceRef(Kind.HISTORICAL_RECORD, null, null, null, null, null, null, null, importId, rowNumber, sourceRecordId, null, null);
    }

    public static EvidenceRef checklistItem(String documentType, String note) {
        return new EvidenceRef(Kind.CHECKLIST_ITEM, null, null, null, null, null, null, null, null, null, null, documentType, note);
    }
}
