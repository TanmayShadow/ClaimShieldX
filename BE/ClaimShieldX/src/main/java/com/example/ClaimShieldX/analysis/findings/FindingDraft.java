package com.example.ClaimShieldX.analysis.findings;

import java.util.List;

/** A finding before it is stored. {@code type}: FACT (a deterministic result), UNKNOWN (a check that could not run), HYPOTHESIS (reviewed model interpretation). */
public record FindingDraft(String source, String category, String title, String summary, String type, String status, String validationState,
                           String rejectionReason, List<String> supportingRefs, List<String> counterRefs, List<String> counterEvidence,
                           List<String> missingChecks, String recommendation) {
    public boolean validated() {
        return "VALIDATED".equals(validationState);
    }
}
