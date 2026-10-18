package com.example.ClaimShieldX.investigation.work;

import com.example.ClaimShieldX.investigation.model.InvestigationErrorCode;
import com.example.ClaimShieldX.investigation.model.StageStatus;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** What a stage handler reports back. The runner decides what each outcome means for the investigation. */
public sealed interface StageOutcome {
    /** The stage finished as OK, DEGRADED (part of the work could not be done) or SKIPPED; the investigation goes on. */
    record Done(StageStatus status, Map<String, Object> summary, UUID ruleRunId) implements StageOutcome {
        public Done {
            if (!status.isSettled()) {
                throw new IllegalArgumentException("A finished stage is OK, DEGRADED or SKIPPED");
            }
            summary = Map.copyOf(summary);
        }
    }

    /**
     * Not a failure: something else holds a document this stage needs (another request is reading it, or a worker died while reading it and
     * the document is still marked as being read). Sleep until it can be taken, without using an attempt: nothing went wrong that trying
     * again would not fix, and waiting is bounded because a document abandoned in that state can be taken again after a timeout.
     */
    record Busy(Instant retryAt, Map<String, Object> summary) implements StageOutcome {
        public Busy {
            summary = Map.copyOf(summary);
        }
    }

    /** Failed in a way that may pass: retried with a delay while attempts remain. */
    record Transient(InvestigationErrorCode code, Map<String, Object> summary) implements StageOutcome {
        public Transient {
            summary = Map.copyOf(summary);
        }
    }

    /** Failed in a way retrying cannot fix. */
    record Permanent(InvestigationErrorCode code, Map<String, Object> summary) implements StageOutcome {
        public Permanent {
            summary = Map.copyOf(summary);
        }
    }

    /** Cannot start until a document the claim lacks arrives. */
    record Waiting(Map<String, Object> summary) implements StageOutcome {
        public Waiting {
            summary = Map.copyOf(summary);
        }
    }

    /** The handler stopped early because the investigation was cancelled or this worker lost its lease. */
    record Interrupted() implements StageOutcome {
    }

    static Done ok(Map<String, Object> summary) {
        return new Done(StageStatus.OK, summary, null);
    }

    static Done degraded(Map<String, Object> summary) {
        return new Done(StageStatus.DEGRADED, summary, null);
    }

    static Done skipped(Map<String, Object> summary) {
        return new Done(StageStatus.SKIPPED, summary, null);
    }
}
