package com.example.ClaimShieldX.investigation.service;

import com.example.ClaimShieldX.analysis.report.ReportService;
import com.example.ClaimShieldX.analysis.review.AuditService;
import com.example.ClaimShieldX.analysis.snapshot.SnapshotService;
import com.example.ClaimShieldX.auth.model.UserPrincipal;
import com.example.ClaimShieldX.claims.model.Claim;
import com.example.ClaimShieldX.claims.repository.ClaimsRepository;
import com.example.ClaimShieldX.common.PagedResponse;
import com.example.ClaimShieldX.common.utils.PageParams;
import com.example.ClaimShieldX.exceptions.InvalidStateTransitionException;
import com.example.ClaimShieldX.exceptions.InvestigationAlreadyActiveException;
import com.example.ClaimShieldX.exceptions.InvestigationQuotaExceededException;
import com.example.ClaimShieldX.exceptions.ResourceNotFoundException;
import com.example.ClaimShieldX.idempotency.service.IdempotencyService;
import com.example.ClaimShieldX.investigation.config.InvestigationProperties;
import com.example.ClaimShieldX.investigation.dto.InvestigationResponse;
import com.example.ClaimShieldX.investigation.model.Investigation;
import com.example.ClaimShieldX.investigation.model.InvestigationStageRow;
import com.example.ClaimShieldX.investigation.repository.InvestigationRepository;
import com.example.ClaimShieldX.investigation.repository.InvestigationStageRepository;
import com.example.ClaimShieldX.investigation.work.InvestigationStore;
import com.example.ClaimShieldX.investigation.work.InvestigationStore.CancelResult;
import com.example.ClaimShieldX.investigation.work.InvestigationStore.RetryResult;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The API side of investigations: create (durably queued before the answer), read, list, retry and cancel. Nothing here does the
 * work; the worker does. Everything is looked up through the caller's organization, so another organization's investigation looks
 * like one that does not exist.
 */
@Service
public class InvestigationService {
    public static final Map<String, String> SORTABLE = Map.of("createdAt", "createdAt", "updatedAt", "updatedAt");
    static final String CREATE = "investigation.create";
    static final String RETRY = "investigation.retry";
    static final String CANCEL = "investigation.cancel";
    private static final String RESOURCE_TYPE = "INVESTIGATION";

    public record Result(InvestigationResponse investigation, boolean replayed) {
    }

    private final ClaimsRepository claimsRepository;
    private final InvestigationRepository repository;
    private final InvestigationStageRepository stageRepository;
    private final InvestigationStore store;
    private final InvestigationRecords records;
    private final IdempotencyService idempotencyService;
    private final InvestigationProperties properties;
    private final Clock clock;
    private final AuditService audit;
    private final ReportService reports;
    private final SnapshotService snapshots;

    public InvestigationService(ClaimsRepository claimsRepository, InvestigationRepository repository, InvestigationStageRepository stageRepository,
                                InvestigationStore store, InvestigationRecords records, IdempotencyService idempotencyService,
                                InvestigationProperties properties, Clock investigationClock,
                                AuditService audit, ReportService reports, SnapshotService snapshots) {
        this.audit = audit;
        this.reports = reports;
        this.snapshots = snapshots;
        this.claimsRepository = claimsRepository;
        this.repository = repository;
        this.stageRepository = stageRepository;
        this.store = store;
        this.records = records;
        this.idempotencyService = idempotencyService;
        this.properties = properties;
        this.clock = investigationClock;
    }

    /** Queues an investigation. The answer is given only after the row is committed, so a 202 can never be lost. */
    public Result create(UserPrincipal principal, UUID claimId, String note, String idempotencyKey) {
        IdempotencyService.validateKey(idempotencyKey);
        UUID organizationId = principal.getOrganization().getId();
        String cleanNote = note == null || note.isBlank() ? null : note.strip();
        if (cleanNote != null && cleanNote.length() > properties.noteMaxLength()) {
            throw com.example.ClaimShieldX.exceptions.RequestValidationException.of("note", "cannot exceed " + properties.noteMaxLength() + " characters");
        }
        Claim claim = claimsRepository.findByIdAndOrganizationId(claimId, organizationId).orElseThrow(() -> new ResourceNotFoundException("Claim not found"));

        IdempotencyService.Begin begin = idempotencyService.begin(organizationId, CREATE, idempotencyKey,
                IdempotencyService.fingerprint(claimId.toString(), cleanNote == null ? "" : cleanNote));
        if (begin.mode() == IdempotencyService.Mode.REPLAY) {
            return new Result(view(organizationId, begin.resourceId()), true);
        }
        try {
            repository.findActive(organizationId, claimId).ifPresent(active -> {
                throw new InvestigationAlreadyActiveException(active.getId());
            });
            Instant now = clock.instant();
            if (repository.countByOrganizationIdAndCreatedAtGreaterThanEqual(organizationId, now.minus(Duration.ofHours(24))) >= properties.dailyLimitPerOrganization()) {
                throw new InvestigationQuotaExceededException();
            }
            Investigation created;
            try {
                created = records.insert(organizationId, claim, principal.getId(), cleanNote, properties.maxAttempts(), now);
            } catch (DataIntegrityViolationException e) {
                // Two requests raced past the check above; the unique index let only one in.
                UUID active = repository.findActive(organizationId, claimId).map(Investigation::getId).orElseThrow(() -> e);
                throw new InvestigationAlreadyActiveException(active);
            }
            idempotencyService.complete(begin.keyId(), 202, RESOURCE_TYPE, created.getId());
            audit.record(organizationId, claimId, created.getId(), principal.getId(), "INVESTIGATION_CREATED", Map.of("claimVersion", claim.getClaimVersion(), "hasNote", cleanNote != null));
            return new Result(view(organizationId, created.getId()), false);
        } catch (RuntimeException e) {
            idempotencyService.release(begin.keyId());
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public InvestigationResponse get(UserPrincipal principal, UUID investigationId) {
        return view(principal.getOrganization().getId(), investigationId);
    }

    @Transactional(readOnly = true)
    public PagedResponse<InvestigationResponse> list(UserPrincipal principal, UUID claimId, Integer page, Integer size, String sort) {
        UUID organizationId = principal.getOrganization().getId();
        if (!claimsRepository.existsByIdAndOrganizationId(claimId, organizationId)) {
            throw new ResourceNotFoundException("Claim not found");
        }
        Pageable pageable = PageParams.resolve(page, size, sort, SORTABLE, "createdAt", Sort.Direction.DESC);
        Page<Investigation> investigations = repository.findByOrganizationIdAndClaimId(organizationId, claimId, pageable);
        Map<UUID, List<InvestigationStageRow>> stages = investigations.isEmpty() ? Map.of()
                : stageRepository.findByOrganizationIdAndInvestigationIdIn(organizationId, investigations.map(Investigation::getId).toList()).stream()
                .collect(Collectors.groupingBy(InvestigationStageRow::getInvestigationId));
        return PagedResponse.of(investigations, i -> withOutputs(organizationId, i, stages.getOrDefault(i.getId(), List.of())));
    }

    /** FAILED goes back to QUEUED as a new attempt; finished stages are kept and the failed one runs again. */
    public Result retry(UserPrincipal principal, UUID investigationId, String idempotencyKey) {
        IdempotencyService.validateKey(idempotencyKey);
        UUID organizationId = principal.getOrganization().getId();
        requireExists(organizationId, investigationId);
        IdempotencyService.Begin begin = idempotencyService.begin(organizationId, RETRY, idempotencyKey, IdempotencyService.fingerprint(investigationId.toString()));
        if (begin.mode() == IdempotencyService.Mode.REPLAY) {
            return new Result(view(organizationId, investigationId), true);
        }
        try {
            RetryResult result;
            try {
                result = store.retryFailed(organizationId, investigationId, 1, clock.instant());
            } catch (DataIntegrityViolationException e) {
                UUID claimId = repository.findByIdAndOrganizationId(investigationId, organizationId).map(Investigation::getClaimId).orElseThrow(() -> e);
                UUID active = repository.findActive(organizationId, claimId).map(Investigation::getId).orElseThrow(() -> e);
                throw new InvestigationAlreadyActiveException(active);
            }
            if (result == RetryResult.NOT_FOUND) {
                throw new ResourceNotFoundException("Investigation not found");
            }
            if (result == RetryResult.NOT_ALLOWED) {
                throw new InvalidStateTransitionException("Only a failed investigation can be retried");
            }
            idempotencyService.complete(begin.keyId(), 202, RESOURCE_TYPE, investigationId);
            audit.record(organizationId, claimOf(organizationId, investigationId), investigationId, principal.getId(), "INVESTIGATION_RETRIED", Map.of());
            return new Result(view(organizationId, investigationId), false);
        } catch (RuntimeException e) {
            idempotencyService.release(begin.keyId());
            throw e;
        }
    }

    /** Cancels at once when nothing is working on it; otherwise the worker stops at its next safe point and the answer says so. */
    public Result cancel(UserPrincipal principal, UUID investigationId, String idempotencyKey) {
        IdempotencyService.validateKey(idempotencyKey);
        UUID organizationId = principal.getOrganization().getId();
        requireExists(organizationId, investigationId);
        IdempotencyService.Begin begin = idempotencyService.begin(organizationId, CANCEL, idempotencyKey, IdempotencyService.fingerprint(investigationId.toString()));
        if (begin.mode() == IdempotencyService.Mode.REPLAY) {
            return new Result(view(organizationId, investigationId), true);
        }
        try {
            CancelResult result = store.requestCancel(organizationId, investigationId, clock.instant());
            if (result == CancelResult.NOT_FOUND) {
                throw new ResourceNotFoundException("Investigation not found");
            }
            if (result == CancelResult.NOT_ALLOWED) {
                throw new InvalidStateTransitionException("The investigation has already finished");
            }
            idempotencyService.complete(begin.keyId(), 200, RESOURCE_TYPE, investigationId);
            audit.record(organizationId, claimOf(organizationId, investigationId), investigationId, principal.getId(), "INVESTIGATION_CANCEL_REQUESTED", Map.of("immediate", result == CancelResult.CANCELLED));
            return new Result(view(organizationId, investigationId), false);
        } catch (RuntimeException e) {
            idempotencyService.release(begin.keyId());
            throw e;
        }
    }

    private void requireExists(UUID organizationId, UUID investigationId) {
        if (repository.findByIdAndOrganizationId(investigationId, organizationId).isEmpty()) {
            throw new ResourceNotFoundException("Investigation not found");
        }
    }

    private UUID claimOf(UUID organizationId, UUID investigationId) {
        return repository.findByIdAndOrganizationId(investigationId, organizationId).map(Investigation::getClaimId).orElseThrow(() -> new ResourceNotFoundException("Investigation not found"));
    }

    private InvestigationResponse view(UUID organizationId, UUID investigationId) {
        Investigation investigation = repository.findByIdAndOrganizationId(investigationId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Investigation not found"));
        return withOutputs(organizationId, investigation, stageRepository.findByOrganizationIdAndInvestigationId(organizationId, investigationId));
    }

    /** Adds what the investigation produced: its snapshot and, once there is one, its report and whether newer evidence has arrived. */
    private InvestigationResponse withOutputs(UUID organizationId, Investigation investigation, List<InvestigationStageRow> rows) {
        InvestigationResponse base = InvestigationResponse.of(investigation, rows);
        var snapshot = snapshots.find(organizationId, investigation.getId());
        var report = reports.findByInvestigation(organizationId, investigation.getId());
        String reportStatus = report.isEmpty() || snapshot.isEmpty() ? null : reports.isStale(organizationId, report.get(), snapshot.get().createdAt()) ? "STALE" : "CURRENT";
        return base.withOutputs(snapshot.map(SnapshotService.Snapshot::id).orElse(null), report.map(ReportService.Report::id).orElse(null), reportStatus);
    }
}
