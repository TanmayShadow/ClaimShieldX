package com.example.ClaimShieldX.investigation.service;

import com.example.ClaimShieldX.claims.model.Claim;
import com.example.ClaimShieldX.investigation.model.Investigation;
import com.example.ClaimShieldX.investigation.model.InvestigationStatus;
import com.example.ClaimShieldX.investigation.repository.InvestigationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** Inserts the queued investigation in its own transaction, so it is committed before the request answers. */
@Service
class InvestigationRecords {
    private final InvestigationRepository repository;

    InvestigationRecords(InvestigationRepository repository) {
        this.repository = repository;
    }

    @Transactional
    Investigation insert(UUID organizationId, Claim claim, UUID requestedBy, String note, int maxAttempts, Instant now) {
        Instant at = now.truncatedTo(ChronoUnit.MICROS);
        Investigation investigation = new Investigation();
        investigation.setOrganizationId(organizationId);
        investigation.setClaimId(claim.getId());
        investigation.setClaimVersion(claim.getClaimVersion());
        investigation.setRequestedBy(requestedBy);
        investigation.setNote(note);
        investigation.setStatus(InvestigationStatus.QUEUED);
        investigation.setAttempt(1);
        investigation.setMaxAttempts(maxAttempts);
        investigation.setCreatedAt(at);
        investigation.setUpdatedAt(at);
        return repository.saveAndFlush(investigation);
    }
}
