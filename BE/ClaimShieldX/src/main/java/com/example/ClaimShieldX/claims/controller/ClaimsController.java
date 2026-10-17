package com.example.ClaimShieldX.claims.controller;

import com.example.ClaimShieldX.auth.model.UserPrincipal;
import com.example.ClaimShieldX.claims.dto.ClaimCreationRequest;
import com.example.ClaimShieldX.claims.dto.ClaimResponse;
import com.example.ClaimShieldX.claims.dto.ClaimSummaryResponse;
import com.example.ClaimShieldX.claims.model.ClaimStage;
import com.example.ClaimShieldX.claims.service.ClaimListService;
import com.example.ClaimShieldX.claims.service.ClaimsService;
import com.example.ClaimShieldX.common.PagedResponse;
import com.example.ClaimShieldX.common.utils.PageParams;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/claims")
public class ClaimsController {
    private static final Map<String, String> SORTABLE = Map.of("createdAt", "createdAt", "updatedAt", "updatedAt", "internalId", "internalId");

    private final ClaimsService claimsService;
    private final ClaimListService claimListService;

    public ClaimsController(ClaimsService claimsService, ClaimListService claimListService){
        this.claimsService = claimsService;
        this.claimListService = claimListService;
    }

    @GetMapping()
    @PreAuthorize("isAuthenticated()")
    public PagedResponse<ClaimSummaryResponse> listClaims(@AuthenticationPrincipal UserPrincipal userPrincipal,
                                                          @RequestParam(value = "stage", required = false) ClaimStage stage,
                                                          @RequestParam(value = "active", defaultValue = "true") boolean active,
                                                          @RequestParam(value = "page", required = false) Integer page,
                                                          @RequestParam(value = "size", required = false) Integer size,
                                                          @RequestParam(value = "sort", required = false) String sort){
        return claimListService.list(userPrincipal, stage, active,
                PageParams.resolve(page, size, sort, SORTABLE, "createdAt", Sort.Direction.DESC));
    }

    @PostMapping()
    @PreAuthorize("hasAnyRole('INVESTIGATOR', 'ORG_ADMIN')")
    public ResponseEntity<ClaimResponse> createClaim(@Valid @RequestBody ClaimCreationRequest claimCreationRequest, @AuthenticationPrincipal UserPrincipal userPrincipal){
        ClaimResponse claim = claimsService.createClaim(claimCreationRequest,userPrincipal);
        return new ResponseEntity<>(claim, HttpStatus.CREATED);
    }

    @GetMapping("/{claimId}")
    @PreAuthorize("isAuthenticated()")
    public ClaimResponse getClaim(@PathVariable UUID claimId, @AuthenticationPrincipal UserPrincipal userPrincipal){
        return claimsService.getClaim(claimId, userPrincipal);
    }
}
