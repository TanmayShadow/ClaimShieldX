package com.example.ClaimShieldX.exceptions;

import com.example.ClaimShieldX.common.ErrorResponse;

import java.util.List;

/** Validation that cannot be expressed as a bean-validation annotation (headers, parts, cross-record rules). */
public class RequestValidationException extends RuntimeException {
    private final List<ErrorResponse.FieldIssue> issues;

    public RequestValidationException(List<ErrorResponse.FieldIssue> issues) {
        super("Request validation failed");
        this.issues = List.copyOf(issues);
    }

    public static RequestValidationException of(String field, String message) {
        return new RequestValidationException(List.of(new ErrorResponse.FieldIssue(field, message)));
    }

    public List<ErrorResponse.FieldIssue> getIssues() {
        return issues;
    }
}
