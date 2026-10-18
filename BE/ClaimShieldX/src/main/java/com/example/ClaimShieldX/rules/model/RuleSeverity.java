package com.example.ClaimShieldX.rules.model;

/**
 * Only present on a FLAG. REVIEW: the data deviates beyond the configured tolerance and the inputs can be relied on, so a
 * person should look. INFO: worth recording, but not yet material (the inputs are doubtful, the deviation has an obvious
 * benign explanation, or it is context). Not a probability of wrongdoing; the review-priority weights belong to B11.
 */
public enum RuleSeverity {
    INFO,
    REVIEW
}
