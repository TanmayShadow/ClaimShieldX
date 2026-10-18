package com.example.ClaimShieldX.rules.controller;

import com.example.ClaimShieldX.auth.model.UserPrincipal;
import com.example.ClaimShieldX.common.PagedResponse;
import com.example.ClaimShieldX.rules.dto.RuleRunResponse;
import com.example.ClaimShieldX.rules.dto.RuleRunSummary;
import com.example.ClaimShieldX.rules.service.RuleRunService;
import com.example.ClaimShieldX.rules.service.RuleRunService.RunOutcome;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/claims/{claimId}/rule-runs")
public class RuleRunController {
    private final RuleRunService service;

    public RuleRunController(RuleRunService service) {
        this.service = service;
    }

    /**
     * Runs every deterministic rule on what the claim has now. No body and no Idempotency-Key: the inputs are hashed, so asking
     * again with nothing new returns the existing run (200) instead of creating another (201).
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('INVESTIGATOR', 'ORG_ADMIN')")
    public ResponseEntity<RuleRunResponse> run(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID claimId) {
        RunOutcome outcome = service.run(principal, claimId);
        return ResponseEntity.status(outcome.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .location(URI.create("/api/v1/claims/" + claimId + "/rule-runs/" + outcome.run().id()))
                .body(outcome.run());
    }

    @GetMapping("/latest")
    @PreAuthorize("isAuthenticated()")
    public RuleRunResponse latest(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID claimId) {
        return service.latest(principal, claimId);
    }

    @GetMapping("/{runId}")
    @PreAuthorize("isAuthenticated()")
    public RuleRunResponse get(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID claimId, @PathVariable UUID runId) {
        return service.get(principal, claimId, runId);
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public PagedResponse<RuleRunSummary> list(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID claimId,
                                              @RequestParam(value = "page", required = false) Integer page,
                                              @RequestParam(value = "size", required = false) Integer size,
                                              @RequestParam(value = "sort", required = false) String sort) {
        return service.list(principal, claimId, page, size, sort);
    }
}
