package com.example.ClaimShieldX.investigation.model;

/** The ordered stages of an investigation. The names are also the investigation statuses while the stage runs. */
public enum Stage {
    PREPARING_EVIDENCE,
    EXTRACTING,
    RUNNING_RULES,
    BUILDING_HYPOTHESES,
    VALIDATING_FINDINGS,
    GENERATING_REPORT;

    public Stage next() {
        Stage[] all = values();
        return ordinal() + 1 < all.length ? all[ordinal() + 1] : null;
    }
}
