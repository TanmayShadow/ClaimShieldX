package com.example.ClaimShieldX.investigation.work;

import com.example.ClaimShieldX.investigation.model.Stage;

/**
 * The work of one stage. A handler must be safe to run again after a crash or a retry: it either finds its earlier result and
 * skips the expensive part, or it is cheap and repeatable. It holds no database transaction while it does slow work.
 */
public interface StageHandler {
    Stage stage();

    StageOutcome execute(StageContext context) throws Exception;

    /**
     * True when running out of retries should end the stage as DEGRADED and carry on (the later stages can still produce
     * something useful), rather than fail the whole investigation.
     */
    default boolean degradesWhenRetriesRunOut() {
        return false;
    }
}
