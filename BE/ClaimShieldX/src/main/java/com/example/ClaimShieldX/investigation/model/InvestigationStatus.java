package com.example.ClaimShieldX.investigation.model;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/** The public state of an investigation (data-contract section 3.6). A running state is named after the stage being worked on. */
public enum InvestigationStatus {
    QUEUED,
    PREPARING_EVIDENCE,
    EXTRACTING,
    RUNNING_RULES,
    BUILDING_HYPOTHESES,
    VALIDATING_FINDINGS,
    GENERATING_REPORT,
    WAITING_FOR_EVIDENCE,
    RETRY_WAIT,
    COMPLETED,
    FAILED,
    CANCELLED;

    private static final Set<InvestigationStatus> FINISHED = EnumSet.of(COMPLETED, FAILED, CANCELLED);

    /** Not finished. A FAILED investigation is not active: it can be retried, but only while no other one is active. */
    public boolean isActive() {
        return !FINISHED.contains(this);
    }

    /** The stage a worker is on when the investigation has this status, if it is a running status. */
    public Optional<Stage> runningStage() {
        for (Stage stage : Stage.values()) {
            if (stage.name().equals(name())) {
                return Optional.of(stage);
            }
        }
        return Optional.empty();
    }

    public static InvestigationStatus of(Stage stage) {
        return valueOf(stage.name());
    }
}
