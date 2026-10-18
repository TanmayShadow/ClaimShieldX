package com.example.ClaimShieldX.investigation.dto;

import jakarta.validation.constraints.Size;

/** An optional note for whoever reads the investigation later. It is never sent to a model. */
public record CreateInvestigationRequest(
        @Size(max = 500, message = "Note cannot exceed 500 characters")
        String note
) {
}
