package com.example.ClaimShieldX.rules.service;

import com.example.ClaimShieldX.claims.model.Claim;
import com.example.ClaimShieldX.claims.repository.ClaimsRepository;
import com.example.ClaimShieldX.common.utils.TextCleaner;
import com.example.ClaimShieldX.documents.model.Document;
import com.example.ClaimShieldX.documents.model.DocumentType;
import com.example.ClaimShieldX.documents.repository.DocumentRepository;
import com.example.ClaimShieldX.documents.repository.DocumentRepository.SameFileRow;
import com.example.ClaimShieldX.extraction.model.DocumentExtraction;
import com.example.ClaimShieldX.extraction.model.ExtractionRunStatus;
import com.example.ClaimShieldX.extraction.repository.DocumentExtractionRepository;
import com.example.ClaimShieldX.extraction.schema.ExtractionResult;
import com.example.ClaimShieldX.extraction.schema.FieldResult;
import com.example.ClaimShieldX.extraction.schema.FieldState;
import com.example.ClaimShieldX.history.dto.HistoryMatchesResponse;
import com.example.ClaimShieldX.history.service.HistoryMatchingService;
import com.example.ClaimShieldX.readiness.dto.ChecklistItem;
import com.example.ClaimShieldX.readiness.dto.ReadinessIssue;
import com.example.ClaimShieldX.readiness.model.InvestigationCheck;
import com.example.ClaimShieldX.readiness.service.ChecklistEvaluator.Evaluation;
import com.example.ClaimShieldX.readiness.service.ReadinessService;
import com.example.ClaimShieldX.rules.engine.InvoiceKey;
import com.example.ClaimShieldX.rules.engine.RuleInputs;
import com.example.ClaimShieldX.rules.engine.RuleInputs.ChecklistFacts;
import com.example.ClaimShieldX.rules.engine.RuleInputs.ClaimFacts;
import com.example.ClaimShieldX.rules.engine.RuleInputs.DocumentFacts;
import com.example.ClaimShieldX.rules.engine.RuleInputs.ExtractionFacts;
import com.example.ClaimShieldX.rules.engine.RuleInputs.FileMatch;
import com.example.ClaimShieldX.rules.engine.RuleInputs.HistoryFacts;
import com.example.ClaimShieldX.rules.engine.RuleInputs.InvoiceIdentity;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Gathers everything the rules may look at, always through the claim's organization, and sorts it into a fixed order. This is
 * the only place the rules touch the database; the rules themselves are pure.
 */
@Service
public class RuleInputLoader {
    private static final int MAX_SAME_FILE_ROWS = 500;
    private static final int MAX_INVOICE_CANDIDATES = 50;

    private final DocumentRepository documentRepository;
    private final DocumentExtractionRepository extractionRepository;
    private final ClaimsRepository claimsRepository;
    private final ReadinessService readinessService;
    private final HistoryMatchingService historyMatchingService;

    public RuleInputLoader(DocumentRepository documentRepository, DocumentExtractionRepository extractionRepository, ClaimsRepository claimsRepository,
                           ReadinessService readinessService, HistoryMatchingService historyMatchingService) {
        this.documentRepository = documentRepository;
        this.extractionRepository = extractionRepository;
        this.claimsRepository = claimsRepository;
        this.readinessService = readinessService;
        this.historyMatchingService = historyMatchingService;
    }

    @Transactional(readOnly = true)
    public RuleInputs load(UUID organizationId, Claim claim) {
        ClaimFacts claimFacts = claimFacts(claim);
        List<Document> documents = documentRepository.findByOrganizationIdAndClaimIdOrderByReceivedAtAscIdAsc(organizationId, claim.getId());
        Map<UUID, DocumentExtraction> newest = documents.isEmpty() ? Map.of()
                : extractionRepository.findNewestByDocuments(organizationId, documents.stream().map(Document::getId).toList()).stream()
                .collect(Collectors.toMap(DocumentExtraction::getDocumentId, Function.identity()));

        List<DocumentFacts> documentFacts = documents.stream().map(d -> documentFacts(d, newest.get(d.getId()))).toList();
        return new RuleInputs(claimFacts, checklist(organizationId, claim), documentFacts,
                fileMatches(organizationId, claim, documents), invoiceCandidates(organizationId, claim, documentFacts), history(organizationId, claim));
    }

    private static ClaimFacts claimFacts(Claim claim) {
        var incident = claim.getIncident();
        var policy = claim.getPolicy();
        var financial = claim.getFinancial();
        return new ClaimFacts(claim.getId(), claim.getInternalId(), claim.getClaimVersion(), claim.getCurrentStage(),
                incident == null ? null : incident.getIncidentType(), incident == null ? null : incident.getIncidentAt(), incident == null ? null : incident.getReportedAt(),
                policy == null ? null : policy.getPolicyStartDate(), policy == null ? null : policy.getPolicyEndDate(),
                financial == null ? null : financial.getInitialEstimateAmount(), financial == null ? null : financial.getApprovedEstimateAmount(),
                financial == null ? null : financial.getCurrency(), claim.getGarage() == null ? null : claim.getGarage().getGarageId(),
                claim.getVehicle() == null ? null : claim.getVehicle().getVehicleToken());
    }

    private static DocumentFacts documentFacts(Document d, DocumentExtraction extraction) {
        ExtractionFacts facts = extraction != null && extraction.getStatus() == ExtractionRunStatus.SUCCEEDED && extraction.getResult() != null
                ? new ExtractionFacts(extraction.getId(), extraction.getAttempt(), extraction.getSchemaVersion(), extraction.getResult()) : null;
        return new DocumentFacts(d.getId(), d.getDocumentType(), d.getStatus(), d.getSha256(), d.getReceivedAt(), d.isSuperseded(), d.getSupersedesDocumentId(), facts);
    }

    private ChecklistFacts checklist(UUID organizationId, Claim claim) {
        Evaluation evaluation = readinessService.evaluateFor(organizationId, claim);
        List<ChecklistFacts.Entry> entries = evaluation.items().stream().map(this::entry).toList();
        List<ChecklistFacts.Issue> issues = new ArrayList<>();
        evaluation.blockers().forEach(i -> issues.add(issue(i, true)));
        evaluation.warnings().forEach(i -> issues.add(issue(i, false)));
        return new ChecklistFacts(evaluation.readiness(), entries, issues);
    }

    private ChecklistFacts.Entry entry(ChecklistItem item) {
        return new ChecklistFacts.Entry(item.documentType(), item.requirement(), item.mandatory(), item.evidenceState(), item.documentIds(), names(item.blocks()));
    }

    private ChecklistFacts.Issue issue(ReadinessIssue issue, boolean blocker) {
        return new ChecklistFacts.Issue(issue.code().name(), issue.documentType(), blocker, names(issue.blocks()));
    }

    private static List<String> names(List<InvestigationCheck> checks) {
        return checks == null ? List.of() : checks.stream().map(Enum::name).toList();
    }

    private List<FileMatch> fileMatches(UUID organizationId, Claim claim, List<Document> documents) {
        Set<String> hashes = documents.stream().filter(d -> !d.isSuperseded()).map(Document::getSha256).collect(Collectors.toSet());
        if (hashes.isEmpty()) {
            return List.of();
        }
        List<SameFileRow> rows = documentRepository.findSameFile(organizationId, hashes, PageRequest.of(0, MAX_SAME_FILE_ROWS));
        String vehicle = claim.getVehicle() == null ? null : claim.getVehicle().getVehicleToken();
        String policy = claim.getPolicy() == null ? null : claim.getPolicy().getPolicyToken();
        List<FileMatch> matches = new ArrayList<>();
        for (Document own : documents) {
            if (own.isSuperseded()) {
                continue;
            }
            for (SameFileRow row : rows) {
                if (row.getSha256().equals(own.getSha256()) && !row.getId().equals(own.getId())) {
                    matches.add(new FileMatch(own.getId(), row.getId(), row.getClaimId(), row.getClaimInternalId(), row.getDocumentType(), row.getStatus(),
                            row.getSuperseded(), row.getSupersedesDocumentId(), row.getReceivedAt(),
                            vehicle != null && vehicle.equals(row.getVehicleToken()), policy != null && policy.equals(row.getPolicyToken())));
                }
            }
        }
        matches.sort(Comparator.comparing(FileMatch::documentId).thenComparing(FileMatch::otherDocumentId));
        return matches;
    }

    /** Other final invoices that share this claim's newest invoice number (after the same reduction the rule applies), read from stored extractions. */
    private List<InvoiceIdentity> invoiceCandidates(UUID organizationId, Claim claim, List<DocumentFacts> documents) {
        DocumentFacts invoice = documents.stream().filter(d -> !d.superseded() && d.type() == DocumentType.FINAL_INVOICE)
                .max(Comparator.comparing(DocumentFacts::receivedAt).thenComparing(DocumentFacts::id)).orElse(null);
        if (invoice == null || invoice.extraction() == null) {
            return List.of();
        }
        String key = InvoiceKey.of(text(invoice.extraction().result(), "documentNumber"));
        if (key == null) {
            return List.of();
        }
        List<UUID> extractionIds = extractionRepository.findInvoiceIdsByNumberKey(organizationId, key, invoice.id(), MAX_INVOICE_CANDIDATES);
        if (extractionIds.isEmpty()) {
            return List.of();
        }
        List<DocumentExtraction> extractions = extractionRepository.findAllById(extractionIds).stream().filter(e -> organizationId.equals(e.getOrganizationId())).toList();
        Map<UUID, Document> docs = documentRepository.findAllById(extractions.stream().map(DocumentExtraction::getDocumentId).toList()).stream()
                .filter(d -> organizationId.equals(d.getOrganizationId())).collect(Collectors.toMap(Document::getId, Function.identity()));
        Map<UUID, Claim> claims = claimsRepository.findAllById(extractions.stream().map(DocumentExtraction::getClaimId).distinct().toList()).stream()
                .filter(c -> organizationId.equals(c.getOrganizationId())).collect(Collectors.toMap(Claim::getId, Function.identity()));
        String vehicle = claim.getVehicle() == null ? null : claim.getVehicle().getVehicleToken();
        String policy = claim.getPolicy() == null ? null : claim.getPolicy().getPolicyToken();

        List<InvoiceIdentity> identities = new ArrayList<>();
        for (DocumentExtraction e : extractions) {
            Document doc = docs.get(e.getDocumentId());
            Claim other = claims.get(e.getClaimId());
            if (doc == null || other == null || e.getResult() == null) {
                continue;
            }
            String number = text(e.getResult(), "documentNumber");
            if (!key.equals(InvoiceKey.of(number))) {
                continue;
            }
            identities.add(new InvoiceIdentity(other.getId(), other.getInternalId(), doc.getId(), e.getId(), doc.getStatus(), doc.isSuperseded(), doc.getSupersedesDocumentId(),
                    doc.getReceivedAt(), number, text(e.getResult(), "garageIdentifier"), other.getGarage() == null ? null : other.getGarage().getGarageId(),
                    amount(e.getResult(), "grandTotal"), text(e.getResult(), "currency"),
                    text(e.getResult(), "documentDate"),
                    vehicle != null && other.getVehicle() != null && vehicle.equals(other.getVehicle().getVehicleToken()),
                    policy != null && other.getPolicy() != null && policy.equals(other.getPolicy().getPolicyToken())));
        }
        identities.sort(Comparator.comparing(InvoiceIdentity::receivedAt).thenComparing(InvoiceIdentity::documentId));
        return identities;
    }

    private HistoryFacts history(UUID organizationId, Claim claim) {
        HistoryMatchesResponse response = historyMatchingService.matchesFor(organizationId, claim.getId());
        var availability = response.dataAvailability();
        List<HistoryFacts.HistoryMatch> matches = response.matches().stream().map(m -> new HistoryFacts.HistoryMatch(m.externalClaimId(), m.claimVersion(),
                m.relation().name(), m.strength().name(), m.timing().name(), (int) Math.min(m.daysBetween(), Integer.MAX_VALUE), m.claimStatus(), m.outcomeStatus(),
                m.ambiguities() == null ? List.of() : m.ambiguities(), m.source().importId(), m.source().rowNumber(), m.source().sourceRecordId())).toList();
        return new HistoryFacts(response.status().name(), availability.latestClaimsImport() == null ? null : availability.latestClaimsImport().id(),
                availability.latestClaimsImportFailed(), response.totalPriorClaims(), response.truncated(), matches);
    }

    private static String text(ExtractionResult result, String name) {
        FieldResult field = field(result, name);
        return field == null || field.state() != FieldState.EXTRACTED ? null : TextCleaner.clean(field.normalizedValue(), 200);
    }

    private static BigDecimal amount(ExtractionResult result, String name) {
        String text = text(result, name);
        try {
            return text == null ? null : new BigDecimal(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static FieldResult field(ExtractionResult result, String name) {
        if (result.fields() == null) {
            return null;
        }
        Map<String, FieldResult> byName = new HashMap<>();
        result.fields().stream().filter(Objects::nonNull).forEach(f -> byName.put(f.name(), f));
        return byName.get(name);
    }
}
