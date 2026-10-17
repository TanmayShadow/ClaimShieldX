package com.example.ClaimShieldX.claims.controller;

import com.example.ClaimShieldX.auth.model.UserPrincipal;
import com.example.ClaimShieldX.claims.dto.ClaimCreationRequest;
import com.example.ClaimShieldX.claims.dto.ClaimResponse;
import com.example.ClaimShieldX.claims.service.ClaimsService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/claims")
public class ClaimsController {
    private final ClaimsService claimsService;

    public ClaimsController(ClaimsService claimsService){
        this.claimsService = claimsService;
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
