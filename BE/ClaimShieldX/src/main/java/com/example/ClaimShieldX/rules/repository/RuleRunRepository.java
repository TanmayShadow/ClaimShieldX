package com.example.ClaimShieldX.rules.repository;

import com.example.ClaimShieldX.rules.model.RuleRun;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RuleRunRepository extends JpaRepository<RuleRun, UUID> {
    // Always through the organization and claim: another organization's run looks like a miss.
    Optional<RuleRun> findByIdAndOrganizationIdAndClaimId(UUID id, UUID organizationId, UUID claimId);

    Optional<RuleRun> findFirstByOrganizationIdAndClaimIdOrderByCreatedAtDescIdDesc(UUID organizationId, UUID claimId);

    Optional<RuleRun> findByOrganizationIdAndClaimIdAndRuleSetVersionAndConfigSha256AndInputFingerprint(UUID organizationId, UUID claimId,
                                                                                                         String ruleSetVersion, String configSha256, String inputFingerprint);

    Page<RuleRun> findByOrganizationIdAndClaimId(UUID organizationId, UUID claimId, Pageable pageable);
}
