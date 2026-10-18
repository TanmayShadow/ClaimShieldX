package com.example.ClaimShieldX.investigation.controller;

import com.example.ClaimShieldX.auth.model.UserPrincipal;
import com.example.ClaimShieldX.common.PagedResponse;
import com.example.ClaimShieldX.investigation.dto.CreateInvestigationRequest;
import com.example.ClaimShieldX.investigation.dto.InvestigationResponse;
import com.example.ClaimShieldX.investigation.service.InvestigationService;
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
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class InvestigationController {
    private final InvestigationService service;

    public InvestigationController(InvestigationService service) {
        this.service = service;
    }

    /** Answers 202 once the investigation is durably queued. Poll the Location for progress. */
    @PostMapping("/claims/{claimId}/investigations")
    @PreAuthorize("hasAnyRole('INVESTIGATOR', 'ORG_ADMIN')")
    public ResponseEntity<InvestigationResponse> create(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID claimId,
                                                        @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                        @RequestBody(required = false) @Valid CreateInvestigationRequest request) {
        InvestigationService.Result result = service.create(principal, claimId, request == null ? null : request.note(), idempotencyKey);
        return accepted(result, HttpStatus.ACCEPTED);
    }

    @GetMapping("/claims/{claimId}/investigations")
    @PreAuthorize("isAuthenticated()")
    public PagedResponse<InvestigationResponse> list(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID claimId,
                                                     @RequestParam(value = "page", required = false) Integer page,
                                                     @RequestParam(value = "size", required = false) Integer size,
                                                     @RequestParam(value = "sort", required = false) String sort) {
        return service.list(principal, claimId, page, size, sort);
    }

    @GetMapping("/investigations/{investigationId}")
    @PreAuthorize("isAuthenticated()")
    public InvestigationResponse get(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID investigationId) {
        return service.get(principal, investigationId);
    }

    @PostMapping("/investigations/{investigationId}/retry")
    @PreAuthorize("hasAnyRole('INVESTIGATOR', 'ORG_ADMIN')")
    public ResponseEntity<InvestigationResponse> retry(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID investigationId,
                                                       @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return accepted(service.retry(principal, investigationId, idempotencyKey), HttpStatus.ACCEPTED);
    }

    /** 200 with the new status: CANCELLED at once, or {@code cancelRequested: true} while a worker is still stopping. */
    @PostMapping("/investigations/{investigationId}/cancel")
    @PreAuthorize("hasAnyRole('INVESTIGATOR', 'ORG_ADMIN')")
    public ResponseEntity<InvestigationResponse> cancel(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID investigationId,
                                                        @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return accepted(service.cancel(principal, investigationId, idempotencyKey), HttpStatus.OK);
    }

    private static ResponseEntity<InvestigationResponse> accepted(InvestigationService.Result result, HttpStatus status) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status).location(URI.create("/api/v1/investigations/" + result.investigation().id()));
        if (result.replayed()) {
            builder.header("Idempotent-Replay", "true");
        }
        return builder.body(result.investigation());
    }
}
