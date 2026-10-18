package com.example.ClaimShieldX.analysis.controller;

import com.example.ClaimShieldX.analysis.AnalysisQueryService;
import com.example.ClaimShieldX.analysis.dto.AnalysisDtos.*;
import com.example.ClaimShieldX.analysis.review.ReviewService;
import com.example.ClaimShieldX.auth.model.UserPrincipal;
import com.example.ClaimShieldX.common.PagedResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class AnalysisController {
    private final AnalysisQueryService queries;
    private final ReviewService reviews;

    public AnalysisController(AnalysisQueryService queries, ReviewService reviews) {
        this.queries = queries;
        this.reviews = reviews;
    }

    @GetMapping("/investigations/{id}/evidence")
    @PreAuthorize("isAuthenticated()")
    public EvidenceResponse evidence(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return queries.evidence(principal, id);
    }

    @GetMapping("/investigations/{id}/graph")
    @PreAuthorize("isAuthenticated()")
    public GraphResponse graph(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return queries.graph(principal, id);
    }

    /** Published findings only. {@code includeRejected} (hypotheses the reviewer did not support, with the reason) is honoured for administrators only. */
    @GetMapping("/investigations/{id}/findings")
    @PreAuthorize("isAuthenticated()")
    public List<FindingView> findings(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id, @RequestParam(value = "includeRejected", defaultValue = "false") boolean includeRejected) {
        return queries.findings(principal, id, includeRejected);
    }

    @GetMapping("/investigations/{id}/report")
    @PreAuthorize("isAuthenticated()")
    public ReportResponse report(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return queries.report(principal, id);
    }

    @GetMapping("/claims/{claimId}/reports/latest")
    @PreAuthorize("isAuthenticated()")
    public ReportResponse latestReport(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID claimId) {
        return queries.latestReport(principal, claimId);
    }

    @PostMapping("/investigations/{id}/actions")
    @PreAuthorize("hasAnyRole('INVESTIGATOR', 'ORG_ADMIN')")
    public ResponseEntity<ReviewActionView> record(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id,
                                                   @RequestHeader("Idempotency-Key") String idempotencyKey, @RequestBody @Valid ActionRequest request) {
        ReviewService.Result result = reviews.record(principal, id, request, idempotencyKey);
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(HttpStatus.CREATED).location(URI.create("/api/v1/investigations/" + id + "/actions"));
        if (result.replayed()) {
            builder.header("Idempotent-Replay", "true");
        }
        return builder.body(result.action());
    }

    @GetMapping("/investigations/{id}/actions")
    @PreAuthorize("isAuthenticated()")
    public List<ReviewActionView> actions(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        queries.report(principal, id);
        return reviews.list(principal.getOrganization().getId(), id);
    }

    @GetMapping("/investigations/{id}/audit")
    @PreAuthorize("isAuthenticated()")
    public PagedResponse<AuditEventView> audit(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id,
                                               @RequestParam(value = "page", required = false) Integer page, @RequestParam(value = "size", required = false) Integer size) {
        return queries.audit(principal, id, page, size);
    }
}
