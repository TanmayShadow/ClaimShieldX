package com.example.ClaimShieldX.readiness.controller;

import com.example.ClaimShieldX.auth.model.UserPrincipal;
import com.example.ClaimShieldX.readiness.dto.DocumentRequirementsResponse;
import com.example.ClaimShieldX.readiness.service.ReadinessService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/claims/{claimId}/document-requirements")
public class DocumentRequirementsController {
    private final ReadinessService readinessService;

    public DocumentRequirementsController(ReadinessService readinessService) {
        this.readinessService = readinessService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public DocumentRequirementsResponse get(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID claimId) {
        return readinessService.checklist(principal, claimId);
    }
}
