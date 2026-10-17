package com.example.ClaimShieldX.claims;

import java.util.UUID;

final class ClaimPayloads {
    private ClaimPayloads() {
    }

    static String uniqueInternalId() {
        return "CLM-" + UUID.randomUUID();
    }

    static String valid(String internalId) {
        return """
                {
                  "internalId": "%s",
                  "claimVersion": 1,
                  "currentStage": "FINAL_INVOICE_RECEIVED",
                  "policy": {
                    "policyToken": "POL-TOKEN-1001",
                    "policyType": "COMPREHENSIVE",
                    "policyStartDate": "2026-01-01T00:00:00+05:30",
                    "policyEndDate": "2026-12-31T23:59:59+05:30"
                  },
                  "claimant": { "claimantToken": "CLT-TOKEN-1001" },
                  "garage": { "garageId": "GARAGE-DEMO-10" },
                  "incident": {
                    "incidentAt": "2026-09-10T10:30:00+05:30",
                    "reportedAt": "2026-09-11T09:00:00+05:30",
                    "permittedLocationPrecision": "CITY"
                  },
                  "financial": {
                    "initialEstimateAmount": 45000,
                    "approvedEstimateAmount": null,
                    "currency": "INR",
                    "deductible": 1000
                  }
                }
                """.formatted(internalId);
    }

    static String valid() {
        return valid(uniqueInternalId());
    }

    static String replace(String json, String from, String to) {
        if (!json.contains(from)) {
            throw new IllegalArgumentException("Test payload does not contain: " + from);
        }
        return json.replace(from, to);
    }
}
