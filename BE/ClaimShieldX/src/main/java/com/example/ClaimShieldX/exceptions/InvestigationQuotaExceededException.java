package com.example.ClaimShieldX.exceptions;

public class InvestigationQuotaExceededException extends RuntimeException {
    public InvestigationQuotaExceededException() {
        super("The organization reached its daily investigation limit");
    }
}
