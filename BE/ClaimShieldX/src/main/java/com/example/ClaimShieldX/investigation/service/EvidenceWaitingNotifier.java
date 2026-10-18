package com.example.ClaimShieldX.investigation.service;

import com.example.ClaimShieldX.investigation.work.InvestigationStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.UUID;

/** Told when a document arrives, so an investigation waiting for evidence looks again at once. Never allowed to fail the upload. */
@Component
public class EvidenceWaitingNotifier {
    private static final Logger log = LoggerFactory.getLogger(EvidenceWaitingNotifier.class);

    private final InvestigationStore store;
    private final Clock clock;

    public EvidenceWaitingNotifier(InvestigationStore store, Clock investigationClock) {
        this.store = store;
        this.clock = investigationClock;
    }

    public void documentAdded(UUID organizationId, UUID claimId) {
        try {
            store.wakeWaiting(organizationId, claimId, clock.instant());
        } catch (RuntimeException e) {
            // The investigation will notice the document at its next scheduled re-check.
            log.warn("Could not wake waiting investigations of claim {}: {}", claimId, e.getClass().getSimpleName());
        }
    }
}
