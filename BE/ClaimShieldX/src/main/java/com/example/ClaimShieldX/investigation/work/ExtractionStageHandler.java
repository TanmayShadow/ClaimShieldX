package com.example.ClaimShieldX.investigation.work;

import com.example.ClaimShieldX.documents.model.Document;
import com.example.ClaimShieldX.documents.model.DocumentStatus;
import com.example.ClaimShieldX.documents.repository.DocumentRepository;
import com.example.ClaimShieldX.exceptions.InvalidStateTransitionException;
import com.example.ClaimShieldX.exceptions.RequestInProgressException;
import com.example.ClaimShieldX.extraction.config.ExtractionProperties;
import com.example.ClaimShieldX.extraction.model.DocumentExtraction;
import com.example.ClaimShieldX.extraction.model.ExtractionFailureCode;
import com.example.ClaimShieldX.extraction.repository.DocumentExtractionRepository;
import com.example.ClaimShieldX.extraction.service.ExtractionOutcome;
import com.example.ClaimShieldX.extraction.service.ExtractionService;
import com.example.ClaimShieldX.investigation.config.InvestigationProperties;
import com.example.ClaimShieldX.investigation.model.InvestigationErrorCode;
import com.example.ClaimShieldX.investigation.model.Stage;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads the documents that have not been read. Safe to run again: a document that is VERIFIED or NEEDS_REVIEW is left alone, so a
 * retry spends model requests only on what is still unread. A transient failure (the model or the store was unavailable) is
 * reported so the stage retries; a document that cannot be read at all is recorded and the stage finishes DEGRADED, because the
 * rules still run on everything else and say what could not be checked.
 */
@Component
public class ExtractionStageHandler implements StageHandler {
    private final DocumentRepository documentRepository;
    private final DocumentExtractionRepository extractionRepository;
    private final ExtractionService extractionService;
    private final ExtractionProperties extractionProperties;
    private final InvestigationProperties properties;
    private final Clock clock;

    public ExtractionStageHandler(DocumentRepository documentRepository, DocumentExtractionRepository extractionRepository, ExtractionService extractionService,
                                  ExtractionProperties extractionProperties, InvestigationProperties properties, Clock investigationClock) {
        this.documentRepository = documentRepository;
        this.extractionRepository = extractionRepository;
        this.extractionService = extractionService;
        this.extractionProperties = extractionProperties;
        this.properties = properties;
        this.clock = investigationClock;
    }

    @Override
    public Stage stage() {
        return Stage.EXTRACTING;
    }

    @Override
    public boolean degradesWhenRetriesRunOut() {
        return true;
    }

    @Override
    public StageOutcome execute(StageContext context) {
        List<Document> documents = documentRepository.findByOrganizationIdAndClaimIdOrderByReceivedAtAscIdAsc(context.organizationId(), context.claimId()).stream()
                .filter(d -> !d.isSuperseded() && ExtractionService.SUPPORTED.contains(d.getDocumentType())).toList();

        List<Map<String, Object>> results = new ArrayList<>();
        InvestigationErrorCode firstTransient = null;
        Instant earliestRelease = null;
        int busy = 0;
        int read = 0;
        int needsReview = 0;
        int alreadyRead = 0;
        int failedForGood = 0;
        int transientFailures = 0;

        for (Document document : documents) {
            if (context.shouldStop().getAsBoolean()) {
                return new StageOutcome.Interrupted();
            }
            Outcome outcome = readOne(context, document);
            results.add(entry(document, outcome));
            switch (outcome.kind) {
                case READ -> read++;
                case ALREADY_READ -> alreadyRead++;
                case NEEDS_REVIEW -> needsReview++;
                case FAILED_FOR_GOOD -> failedForGood++;
                case BUSY -> {
                    busy++;
                    if (earliestRelease == null || outcome.retryAt.isBefore(earliestRelease)) {
                        earliestRelease = outcome.retryAt;
                    }
                }
                case FAILED_LATER_MAY_PASS -> {
                    transientFailures++;
                    if (firstTransient == null) {
                        firstTransient = outcome.code;
                    }
                }
            }
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("documents", results);
        summary.put("read", read);
        summary.put("alreadyRead", alreadyRead);
        summary.put("needsReview", needsReview);
        summary.put("failed", failedForGood);
        summary.put("willRetry", transientFailures);
        summary.put("busy", busy);
        if (firstTransient != null) {
            return new StageOutcome.Transient(firstTransient, summary);
        }
        if (earliestRelease != null) {
            return new StageOutcome.Busy(earliestRelease, summary);
        }
        if (documents.isEmpty()) {
            return StageOutcome.skipped(summary);
        }
        return failedForGood > 0 || needsReview > 0 ? StageOutcome.degraded(summary) : StageOutcome.ok(summary);
    }

    private enum Kind {READ, ALREADY_READ, NEEDS_REVIEW, FAILED_FOR_GOOD, FAILED_LATER_MAY_PASS, BUSY}

    private record Outcome(Kind kind, InvestigationErrorCode code, Instant retryAt) {
        Outcome(Kind kind, InvestigationErrorCode code) {
            this(kind, code, null);
        }
    }

    private Outcome readOne(StageContext context, Document document) {
        DocumentStatus status = document.getStatus();
        if (status == DocumentStatus.VERIFIED) {
            return new Outcome(Kind.ALREADY_READ, null);
        }
        if (status == DocumentStatus.NEEDS_REVIEW) {
            return new Outcome(Kind.NEEDS_REVIEW, null);
        }
        if (status == DocumentStatus.PROCESSING) {
            // Another request is reading it, or a worker died while reading it. Either way it can be taken again once its processing
            // timeout has passed, and the extraction service does that itself; until then wait for it, using no attempt.
            Instant now = clock.instant();
            Instant takeableAt = document.getUpdatedAt().plus(extractionProperties.processingTimeout());
            if (now.isBefore(takeableAt)) {
                Instant soonest = now.plus(properties.waitingRecheck());
                Instant retryAt = soonest.isBefore(takeableAt.plusSeconds(1)) ? soonest : takeableAt.plusSeconds(1);
                return new Outcome(Kind.BUSY, null, retryAt);
            }
        }
        if (status == DocumentStatus.FAILED) {
            ExtractionFailureCode earlier = latestFailure(document);
            if (earlier != null && !earlier.retryable()) {
                return new Outcome(Kind.FAILED_FOR_GOOD, map(earlier));
            }
        }
        try {
            ExtractionOutcome outcome = extractionService.extract(context.organizationId(), context.claimId(), document.getId());
            return switch (outcome.documentStatus()) {
                case VERIFIED -> new Outcome(Kind.READ, null);
                case NEEDS_REVIEW -> new Outcome(Kind.NEEDS_REVIEW, null);
                default -> failureOf(document);
            };
        } catch (RequestInProgressException e) {
            // Someone started reading it between our look and our request.
            return new Outcome(Kind.BUSY, null, clock.instant().plus(properties.waitingRecheck()));
        } catch (InvalidStateTransitionException e) {
            // The per-document attempt limit, or the document changed state under us: nothing more can be done here.
            return new Outcome(Kind.FAILED_FOR_GOOD, InvestigationErrorCode.EXTRACTION_FAILED);
        }
    }

    private Outcome failureOf(Document document) {
        ExtractionFailureCode code = latestFailure(document);
        if (code == null) {
            return new Outcome(Kind.FAILED_LATER_MAY_PASS, InvestigationErrorCode.INTERNAL_ERROR);
        }
        return new Outcome(code.retryable() ? Kind.FAILED_LATER_MAY_PASS : Kind.FAILED_FOR_GOOD, map(code));
    }

    private ExtractionFailureCode latestFailure(Document document) {
        Optional<DocumentExtraction> latest = extractionRepository.findFirstByOrganizationIdAndDocumentIdOrderByAttemptDesc(document.getOrganizationId(), document.getId());
        return latest.map(DocumentExtraction::getFailureCode).orElse(null);
    }

    /** The extraction failure, as the code a client sees. A rate limit is reported as a quota problem: that is what it means to the user. */
    static InvestigationErrorCode map(ExtractionFailureCode code) {
        return switch (code) {
            case MODEL_RATE_LIMITED -> InvestigationErrorCode.MODEL_QUOTA_EXHAUSTED;
            case MODEL_UNAVAILABLE -> InvestigationErrorCode.MODEL_UNAVAILABLE;
            case MODEL_OUTPUT_INVALID -> InvestigationErrorCode.INVALID_MODEL_OUTPUT;
            case STORAGE_UNAVAILABLE -> InvestigationErrorCode.STORAGE_UNAVAILABLE;
            case INTERNAL_ERROR -> InvestigationErrorCode.INTERNAL_ERROR;
            case DOCUMENT_TOO_LARGE, UNREADABLE_FILE, STORED_FILE_MISMATCH, MODEL_REJECTED_INPUT, MODEL_BLOCKED, MODEL_NOT_CONFIGURED -> InvestigationErrorCode.EXTRACTION_FAILED;
        };
    }

    private static Map<String, Object> entry(Document document, Outcome outcome) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("documentId", document.getId().toString());
        entry.put("documentType", document.getDocumentType().name());
        entry.put("outcome", outcome.kind.name());
        if (outcome.code != null) {
            entry.put("code", outcome.code.name());
        }
        return entry;
    }
}
