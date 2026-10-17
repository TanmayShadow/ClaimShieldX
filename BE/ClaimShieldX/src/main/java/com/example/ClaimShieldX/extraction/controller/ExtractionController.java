package com.example.ClaimShieldX.extraction.controller;

import com.example.ClaimShieldX.auth.model.UserPrincipal;
import com.example.ClaimShieldX.extraction.dto.ExtractionResponse;
import com.example.ClaimShieldX.extraction.dto.ExtractionStartedResponse;
import com.example.ClaimShieldX.extraction.service.ExtractionOutcome;
import com.example.ClaimShieldX.extraction.service.ExtractionQueryService;
import com.example.ClaimShieldX.extraction.service.ExtractionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/claims/{claimId}/documents/{documentId}/extraction")
public class ExtractionController {
    private final ExtractionService extractionService;
    private final ExtractionQueryService queryService;

    public ExtractionController(ExtractionService extractionService, ExtractionQueryService queryService) {
        this.extractionService = extractionService;
        this.queryService = queryService;
    }

    /** Reads (or re-reads) the document with the model. See {@link ExtractionStartedResponse} for why this answers 202. */
    @PostMapping
    @PreAuthorize("hasAnyRole('INVESTIGATOR', 'ORG_ADMIN')")
    public ResponseEntity<ExtractionStartedResponse> start(@AuthenticationPrincipal UserPrincipal principal,
                                                           @PathVariable UUID claimId, @PathVariable UUID documentId) {
        ExtractionOutcome outcome = extractionService.extract(principal, claimId, documentId);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .location(URI.create("/api/v1/claims/" + claimId + "/documents/" + documentId + "/extraction"))
                .body(new ExtractionStartedResponse(outcome.documentId(), outcome.documentStatus(), outcome.extractionId(), outcome.attempt()));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ExtractionResponse latest(@AuthenticationPrincipal UserPrincipal principal,
                                     @PathVariable UUID claimId, @PathVariable UUID documentId) {
        return queryService.latest(principal, claimId, documentId);
    }
}
