package com.example.ClaimShieldX.documents.model;

// Candidate types from PRD section 6. Which ones are required is configuration (B4), not code.
public enum DocumentType {
    CLAIM_FORM,
    POLICY_RECORD,
    VEHICLE_REGISTRATION,
    DRIVING_LICENCE,
    DAMAGE_PHOTOGRAPH,
    GARAGE_ESTIMATE,
    JOB_CARD,
    SURVEYOR_REPORT,
    FINAL_INVOICE,
    SUPPLEMENTARY_ESTIMATE,
    PAYMENT_PROOF,
    FIR_POLICE_REPORT,
    THEFT_EVIDENCE,
    OTHER
}
