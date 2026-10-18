package com.example.ClaimShieldX.history.dto;

/** Every row of an import is in exactly one of the three outcomes, so the three add up to the total. */
public record ImportCounts(int totalRows, int accepted, int rejected, int skippedDuplicate) {
}
