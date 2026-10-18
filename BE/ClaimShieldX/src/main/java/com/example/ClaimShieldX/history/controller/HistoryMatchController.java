package com.example.ClaimShieldX.history.controller;

import com.example.ClaimShieldX.auth.model.UserPrincipal;
import com.example.ClaimShieldX.history.dto.HistoryMatchesResponse;
import com.example.ClaimShieldX.history.service.HistoryMatchingService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/claims/{claimId}/history-matches")
public class HistoryMatchController {
    private final HistoryMatchingService matchingService;

    public HistoryMatchController(HistoryMatchingService matchingService) {
        this.matchingService = matchingService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public HistoryMatchesResponse matches(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID claimId) {
        return matchingService.matchesFor(principal, claimId);
    }
}
