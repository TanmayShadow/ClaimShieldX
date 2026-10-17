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
                  "vehicle": { "vehicleToken": "VEH-TOKEN-1001", "make": "DEMO_MAKE", "model": "DEMO_MODEL" },
                  "incident": {
                    "incidentAt": "2026-09-10T10:30:00+05:30",
                    "reportedAt": "2026-09-11T09:00:00+05:30",
                    "incidentType": "COLLISION",
                    "permittedLocationPrecision": "CITY"
                  },
                  "financial": {
                    "initialEstimateAmount": 45000.50,
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
