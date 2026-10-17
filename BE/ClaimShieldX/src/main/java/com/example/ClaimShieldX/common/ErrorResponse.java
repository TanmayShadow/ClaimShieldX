package com.example.ClaimShieldX.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The single error body for every non-2xx response. Clients branch on {@code code}, never on {@code message}.
 * There is deliberately no constructor without a code.
 */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    private final Instant timestamp;
    private final int status;
    private final String error;
    private final String code;
    private final String message;
    private final List<FieldIssue> errors;
    private final Map<String, String> details;

    public ErrorResponse(HttpStatus status, String code, String message) {
        this(status, code, message, null, null);
    }

    public ErrorResponse(HttpStatus status, String code, String message, List<FieldIssue> errors) {
        this(status, code, message, errors, null);
    }

    public ErrorResponse(HttpStatus status, String code, String message, List<FieldIssue> errors, Map<String, String> details) {
        this.timestamp = Instant.now();
        this.status = status.value();
        this.error = status.getReasonPhrase();
        this.code = code;
        this.message = message;
        this.errors = errors;
        this.details = details;
    }

    public record FieldIssue(String field, String message) {
    }
}
