package com.example.ClaimShieldX.rules.engine;

import com.example.ClaimShieldX.claims.model.ClaimStage;
import com.example.ClaimShieldX.claims.model.IncidentType;
import com.example.ClaimShieldX.documents.model.DocumentStatus;
import com.example.ClaimShieldX.documents.model.DocumentType;
import com.example.ClaimShieldX.extraction.schema.ExtractionResult;
import com.example.ClaimShieldX.readiness.model.EvidenceState;
import com.example.ClaimShieldX.readiness.model.Readiness;
import com.example.ClaimShieldX.readiness.model.RequirementLevel;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Everything the rules are allowed to look at, gathered once before any rule runs. The rules are pure functions of this
 * structure and the configuration: no database, no clock, no model. That is what makes a run reproducible, and a run's
 * fingerprint is a hash of this structure, so identical inputs are recognized as identical.
 *
 * <p>Lists are in a fixed order (the loader sorts them) so that equal data always serializes to equal text.
 */
public record RuleInputs(
        ClaimFacts claim,
        ChecklistFacts checklist,
        List<DocumentFacts> documents,
        List<FileMatch> fileMatches,
        List<InvoiceIdentity> invoiceCandidates,
        HistoryFacts history
) {
    public RuleInputs {
        documents = List.copyOf(documents);
        fileMatches = List.copyOf(fileMatches);
        invoiceCandidates = List.copyOf(invoiceCandidates);
    }

    public record ClaimFacts(UUID id, String internalId, long claimVersion, ClaimStage stage, IncidentType incidentType,
                             Instant incidentAt, Instant reportedAt, Instant policyStart, Instant policyEnd,
                             BigDecimal initialEstimate, BigDecimal approvedEstimate, String currency, String garageId, String vehicleToken) {
    }

    /** The checklist outcome of B4 for this claim, so evidence gaps are one more rule result rather than a separate report. */
    public record ChecklistFacts(Readiness readiness, List<Entry> entries, List<Issue> issues) {
        public ChecklistFacts {
            entries = List.copyOf(entries);
            issues = List.copyOf(issues);
        }

        public record Entry(DocumentType documentType, RequirementLevel requirement, boolean mandatory, EvidenceState state,
                            List<UUID> documentIds, List<String> blocks) {
            public Entry {
                documentIds = List.copyOf(documentIds);
                blocks = List.copyOf(blocks);
            }
        }

        public record Issue(String code, DocumentType documentType, boolean blocker, List<String> blocks) {
            public Issue {
                blocks = List.copyOf(blocks);
            }
        }
    }

    /**
     * A document of the claim, current or superseded. The extraction is the newest attempt, if it succeeded; its content is
     * not part of the fingerprint because an extraction row never changes, so its id stands for it.
     */
    public record DocumentFacts(UUID id, DocumentType type, DocumentStatus status, String sha256, Instant receivedAt,
                                boolean superseded, UUID supersedesDocumentId, ExtractionFacts extraction) {
    }

    public record ExtractionFacts(UUID extractionId, int attempt, String schemaVersion, @JsonIgnore ExtractionResult result) {
    }

    /** Another document anywhere in the organization with the same bytes as one of this claim's documents. */
    public record FileMatch(UUID documentId, UUID otherDocumentId, UUID otherClaimId, String otherClaimInternalId, DocumentType otherType,
                            DocumentStatus otherStatus, boolean otherSuperseded, UUID otherSupersedesDocumentId, Instant otherReceivedAt,
                            boolean sameVehicleToken, boolean samePolicyToken) {
    }

    /**
     * The identity of a final invoice that shares an invoice number with this claim's invoice: other claims, and other
     * versions of the invoice on this claim. Read from the stored extraction, never from the model again. {@code claimGarageId} is
     * the garage recorded on that invoice's claim: the issuer to fall back on when the invoice prints no garage identifier.
     */
    public record InvoiceIdentity(UUID claimId, String claimInternalId, UUID documentId, UUID extractionId, DocumentStatus documentStatus,
                                  boolean superseded, UUID supersedesDocumentId, Instant receivedAt, String invoiceNumber, String garageIdentifier,
                                  String claimGarageId, BigDecimal grandTotal, String currency, String documentDate, boolean sameVehicleToken, boolean samePolicyToken) {
    }

    /** The result of the history lookup of B6, reduced to what the rule needs. */
    public record HistoryFacts(String status, UUID latestClaimsImportId, boolean latestImportFailed, int totalPriorClaims, boolean truncated,
                               List<HistoryMatch> matches) {
        public HistoryFacts {
            matches = List.copyOf(matches);
        }

        public record HistoryMatch(String externalClaimId, int claimVersion, String relation, String strength, String timing, Integer daysBetween,
                                   String claimStatus, String outcomeStatus, List<String> ambiguities, UUID importId, int rowNumber,
                                   String sourceRecordId) {
            public HistoryMatch {
                ambiguities = List.copyOf(ambiguities);
            }
        }
    }
}
