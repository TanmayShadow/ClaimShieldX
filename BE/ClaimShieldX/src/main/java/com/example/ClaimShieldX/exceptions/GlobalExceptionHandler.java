package com.example.ClaimShieldX.exceptions;

import com.example.ClaimShieldX.common.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(EmailAlreadyExistException.class)
    public ResponseEntity<ErrorResponse> handleEmailAlreadyExistException(EmailAlreadyExistException e){
        return new ResponseEntity<ErrorResponse>(
                new ErrorResponse(HttpStatus.CONFLICT.value(), HttpStatus.CONFLICT.getReasonPhrase(),"Email already exist" ),
                HttpStatus.CONFLICT
        );
    }

    @ExceptionHandler(OrganizationNameAlreadyExistException.class)
    public ResponseEntity<ErrorResponse> handleOrganizationNameAlreadyExistException(OrganizationNameAlreadyExistException e){
        return new ResponseEntity<ErrorResponse>(
                new ErrorResponse(HttpStatus.CONFLICT.value(), HttpStatus.CONFLICT.getReasonPhrase(),"Organization already exist" ),
                HttpStatus.CONFLICT
        );
    }

    @ExceptionHandler(OrganizationNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleOrganizationNotFoundException(OrganizationNotFoundException e){
        return new ResponseEntity<ErrorResponse>(
                new ErrorResponse(HttpStatus.NOT_FOUND.value(), HttpStatus.NOT_FOUND.getReasonPhrase(),"Organization not found" ),
                HttpStatus.NOT_FOUND
        );
    }

    @ExceptionHandler(RefreshTokenExpiredException.class)
    public ResponseEntity<ErrorResponse> handleRefreshTokenExpiredException(RefreshTokenExpiredException e){
        return new ResponseEntity<ErrorResponse>(
                new ErrorResponse(HttpStatus.UNAUTHORIZED.value(), HttpStatus.UNAUTHORIZED.getReasonPhrase(),"Your session has expired. Please log in again." ),
                HttpStatus.UNAUTHORIZED
        );
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(AuthenticationException e){
        return new ResponseEntity<ErrorResponse>(
                new ErrorResponse(HttpStatus.UNAUTHORIZED.value(), HttpStatus.UNAUTHORIZED.getReasonPhrase(),"Your session has expired. Please log in again." ),
                HttpStatus.UNAUTHORIZED
        );
    }

    @ExceptionHandler(HttpClientErrorException.Forbidden.class)
    public ResponseEntity<ErrorResponse> handleForbidden(HttpClientErrorException.Forbidden e){
        return new ResponseEntity<ErrorResponse>(
                new ErrorResponse(HttpStatus.UNAUTHORIZED.value(), HttpStatus.UNAUTHORIZED.getReasonPhrase(),"Authorization header is missing or access-token token in invalid" ),
                HttpStatus.UNAUTHORIZED
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e){
        List<ErrorResponse.FieldIssue> issues = e.getBindingResult().getFieldErrors().stream()
                .map(error -> new ErrorResponse.FieldIssue(error.getField(), error.getDefaultMessage()))
                .toList();
        return new ResponseEntity<ErrorResponse>(
                new ErrorResponse(HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(),"Request validation failed", issues),
                HttpStatus.BAD_REQUEST
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException e){
        return new ResponseEntity<ErrorResponse>(
                new ErrorResponse(HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(),"Request body is missing, malformed, or contains an invalid value"),
                HttpStatus.BAD_REQUEST
        );
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException e){
        return new ResponseEntity<ErrorResponse>(
                new ErrorResponse(HttpStatus.METHOD_NOT_ALLOWED.value(), HttpStatus.METHOD_NOT_ALLOWED.getReasonPhrase(),"HTTP method is not supported for this endpoint"),
                HttpStatus.METHOD_NOT_ALLOWED
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception e){
        log.error("Unhandled exception", e);
        return new ResponseEntity<ErrorResponse>(
                new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),"An unexpected error occurred"),
                HttpStatus.INTERNAL_SERVER_ERROR
        );
    }

    @ExceptionHandler(DuplicateClaimException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateClaimException(DuplicateClaimException e){
        return new ResponseEntity<ErrorResponse>(
                new ErrorResponse(HttpStatus.CONFLICT.value(), HttpStatus.CONFLICT.getReasonPhrase(),"A claim with same internal id already exist" ),
                HttpStatus.CONFLICT
        );
    }
}
