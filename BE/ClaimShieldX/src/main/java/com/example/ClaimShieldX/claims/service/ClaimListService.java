package com.example.ClaimShieldX.claims.service;

import com.example.ClaimShieldX.auth.model.UserPrincipal;
import com.example.ClaimShieldX.claims.dto.ClaimSummaryResponse;
import com.example.ClaimShieldX.claims.model.Claim;
import com.example.ClaimShieldX.claims.model.ClaimStage;
import com.example.ClaimShieldX.claims.repository.ClaimsRepository;
import com.example.ClaimShieldX.common.PagedResponse;
import com.example.ClaimShieldX.readiness.dto.ReadinessSummary;
import com.example.ClaimShieldX.readiness.service.ReadinessService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/** Lists the caller's own organization's claims. The organization comes from the authenticated user, never from the request. */
@Service
public class ClaimListService {
    private final ClaimsRepository claimsRepository;
    private final ReadinessService readinessService;

    public ClaimListService(ClaimsRepository claimsRepository, ReadinessService readinessService) {
        this.claimsRepository = claimsRepository;
        this.readinessService = readinessService;
    }

    /** @param stage optional filter; null means every stage */
    @Transactional(readOnly = true)
    public PagedResponse<ClaimSummaryResponse> list(UserPrincipal principal, ClaimStage stage, boolean active, Pageable pageable) {
        UUID organizationId = principal.getOrganization().getId();
        Page<Claim> page = stage == null
                ? claimsRepository.findPage(organizationId, active, pageable)
                : claimsRepository.findPageByStage(organizationId, active, stage, pageable);
        Map<UUID, ReadinessSummary> readiness = readinessService.summarize(organizationId, page.getContent());
        return PagedResponse.of(page, claim -> new ClaimSummaryResponse(claim.getId(), claim.getInternalId(),
                claim.getCurrentStage(), claim.getCreatedAt(), readiness.get(claim.getId())));
    }
}
