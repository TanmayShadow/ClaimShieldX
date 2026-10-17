package com.example.ClaimShieldX.readiness.dto;

import com.example.ClaimShieldX.readiness.model.Readiness;

/** The short form shown in the claim list. The full explanation is in the document-requirements response. */
public record ReadinessSummary(Readiness level, int blockerCount, int warningCount) {
}
