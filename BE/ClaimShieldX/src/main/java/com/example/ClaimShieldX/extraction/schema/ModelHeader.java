package com.example.ClaimShieldX.extraction.schema;

public record ModelHeader(
        ModelField documentNumber,
        ModelField documentDate,
        ModelField incidentDate,
        ModelField repairStartDate,
        ModelField repairCompletionDate,
        ModelField garageIdentifier,
        ModelField vehicleIdentifier,
        ModelField currency,
        ModelField repairDescription
) {
}
