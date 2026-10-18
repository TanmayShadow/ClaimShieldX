package com.example.ClaimShieldX.exceptions;

/** A decision names a report version other than the report of that investigation. */
public class ReportVersionMismatchException extends RuntimeException {
    public ReportVersionMismatchException() {
        super("The report version does not match this investigation's report");
    }
}
