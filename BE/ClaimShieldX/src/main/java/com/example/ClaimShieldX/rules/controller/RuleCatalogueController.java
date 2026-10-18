package com.example.ClaimShieldX.rules.controller;

import com.example.ClaimShieldX.rules.dto.RuleCatalogueResponse;
import com.example.ClaimShieldX.rules.service.RuleRunService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/rules")
public class RuleCatalogueController {
    private final RuleRunService service;

    public RuleCatalogueController(RuleRunService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public RuleCatalogueResponse catalogue() {
        return service.catalogue();
    }
}
