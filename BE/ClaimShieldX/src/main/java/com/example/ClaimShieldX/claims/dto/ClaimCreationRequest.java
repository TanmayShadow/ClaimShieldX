package com.example.ClaimShieldX.claims.dto;

import com.example.ClaimShieldX.claims.model.ClaimStage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ClaimCreationRequest(
         @NotBlank(message = "Internal Id cannot be blank")
         @Size(max = 255, message = "Internal Id cannot exceed 255 characters")
         String internalId,
         @NotNull(message = "Claim version is required")
         @Positive(message = "Claim version must be positive")
         Long claimVersion,
         @NotNull(message = "Current stage is required")
         ClaimStage currentStage,
         @NotNull(message = "Policy details cannot be null")
         @Valid
         PolicyDto policy,
         @Valid
         ClaimantDto claimant,
         @NotNull(message = "Garage details cannot be null")
         @Valid
         GarageDto garage,
         @NotNull(message = "Incident details cannot be null")
         @Valid
         IncidentDto incident,
         @NotNull(message = "Financial details cannot be null")
         @Valid
         FinancialDto financial
) {
}
