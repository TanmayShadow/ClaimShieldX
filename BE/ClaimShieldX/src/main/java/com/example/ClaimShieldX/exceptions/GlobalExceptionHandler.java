package com.example.ClaimShieldX.exceptions;

import com.example.ClaimShieldX.common.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/**
 * Every response carries a stable {@code code}; clients branch on it, never on the message. Messages are
 * fixed text: no exception message, SQL, class name or rejected value reaches a client.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    private ResponseEntity<ErrorResponse> respond(HttpStatus status, String code, String message){
        return new ResponseEntity<ErrorResponse>(new ErrorResponse(status, code, message), status);
    }

    @ExceptionHandler(EmailAlreadyExistException.class)
    public ResponseEntity<ErrorResponse> handleEmailAlreadyExistException(EmailAlreadyExistException e){
        return respond(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", "Email is already registered");
    }

    @ExceptionHandler(OrganizationNameAlreadyExistException.class)
    public ResponseEntity<ErrorResponse> handleOrganizationNameAlreadyExistException(OrganizationNameAlreadyExistException e){
        return respond(HttpStatus.CONFLICT, "ORGANIZATION_ALREADY_EXISTS", "Organization already exists");
    }

    @ExceptionHandler(OrganizationNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleOrganizationNotFoundException(OrganizationNotFoundException e){
        return respond(HttpStatus.NOT_FOUND, "ORGANIZATION_NOT_FOUND", "Organization not found");
    }

    @ExceptionHandler(RefreshTokenExpiredException.class)
    public ResponseEntity<ErrorResponse> handleRefreshTokenExpiredException(RefreshTokenExpiredException e){
        return respond(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_EXPIRED", "The refresh token has expired. Please log in again.");
    }

    @ExceptionHandler(RefreshTokenInvalidException.class)
    public ResponseEntity<ErrorResponse> handleRefreshTokenInvalidException(RefreshTokenInvalidException e){
        return respond(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_INVALID", "The refresh token is not valid. Please log in again.");
    }

    // Wrong password and unknown email are deliberately indistinguishable.
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException e){
        return respond(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password");
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(AuthenticationException e){
        return respond(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required");
    }

    // Thrown by method security inside a controller; without this the catch-all below would turn it into a 500.
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e){
        return respond(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have permission to perform this action");
    }

    @ExceptionHandler(DuplicateClaimException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateClaimException(DuplicateClaimException e){
        return respond(HttpStatus.CONFLICT, "DUPLICATE_CLAIM", "A claim with the same internal id already exists");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e){
        List<ErrorResponse.FieldIssue> issues = e.getBindingResult().getFieldErrors().stream()
                .map(error -> new ErrorResponse.FieldIssue(error.getField(), error.getDefaultMessage()))
                .toList();
        return new ResponseEntity<ErrorResponse>(
                new ErrorResponse(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", issues),
                HttpStatus.BAD_REQUEST
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException e){
        return respond(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Request body is missing, malformed, or contains an invalid value");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException e){
        return respond(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "HTTP method is not supported for this endpoint");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e){
        return respond(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "The request content type is not supported for this endpoint");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException e){
        return respond(HttpStatus.NOT_FOUND, "NOT_FOUND", "The requested resource was not found");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception e){
        log.error("Unhandled exception", e);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred");
    }
}
