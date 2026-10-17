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
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.Map;

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

    private ResponseEntity<ErrorResponse> fieldError(String field, String message){
        return new ResponseEntity<ErrorResponse>(
                new ErrorResponse(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed",
                        List.of(new ErrorResponse.FieldIssue(field, message))),
                HttpStatus.BAD_REQUEST
        );
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

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException e){
        return respond(HttpStatus.NOT_FOUND, "NOT_FOUND", "The requested resource was not found");
    }

    @ExceptionHandler(RequestValidationException.class)
    public ResponseEntity<ErrorResponse> handleRequestValidation(RequestValidationException e){
        return new ResponseEntity<ErrorResponse>(
                new ErrorResponse(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", e.getIssues()),
                HttpStatus.BAD_REQUEST
        );
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException e){
        if("Idempotency-Key".equalsIgnoreCase(e.getHeaderName())){
            return respond(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED", "The Idempotency-Key header is required for this request");
        }
        return fieldError(e.getHeaderName(), "is required");
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> handleMissingPart(MissingServletRequestPartException e){
        return fieldError(e.getRequestPartName(), "is required");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException e){
        return fieldError(e.getParameterName(), "is required");
    }

    // The rejected value is never echoed back.
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e){
        return fieldError(e.getName(), "has an invalid value");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleUploadTooLarge(MaxUploadSizeExceededException e){
        return respond(HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE", "The uploaded file is larger than the allowed size");
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ErrorResponse> handleMultipartProblem(MultipartException e){
        return respond(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "The multipart request could not be read");
    }

    @ExceptionHandler(EmptyFileException.class)
    public ResponseEntity<ErrorResponse> handleEmptyFile(EmptyFileException e){
        return respond(HttpStatus.BAD_REQUEST, "EMPTY_FILE", "The uploaded file is empty");
    }

    @ExceptionHandler(UnsupportedFileTypeException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedFileType(UnsupportedFileTypeException e){
        return respond(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "Only PDF, JPEG and PNG files are accepted");
    }

    @ExceptionHandler(DuplicateDocumentException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateDocument(DuplicateDocumentException e){
        return new ResponseEntity<ErrorResponse>(
                new ErrorResponse(HttpStatus.CONFLICT, "DUPLICATE_DOCUMENT", "The same file is already stored for this claim and document type",
                        null, Map.of("existingDocumentId", e.getExistingDocumentId().toString())),
                HttpStatus.CONFLICT
        );
    }

    @ExceptionHandler(DocumentLimitReachedException.class)
    public ResponseEntity<ErrorResponse> handleDocumentLimit(DocumentLimitReachedException e){
        return respond(HttpStatus.CONFLICT, "DOCUMENT_LIMIT_REACHED", "This claim has reached the maximum number of documents");
    }

    @ExceptionHandler(InvalidStateTransitionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidStateTransition(InvalidStateTransitionException e){
        return respond(HttpStatus.CONFLICT, "INVALID_STATE_TRANSITION", "The requested change is not allowed in the current state");
    }

    @ExceptionHandler(IdempotencyKeyReusedException.class)
    public ResponseEntity<ErrorResponse> handleIdempotencyKeyReused(IdempotencyKeyReusedException e){
        return respond(HttpStatus.UNPROCESSABLE_ENTITY, "IDEMPOTENCY_KEY_REUSED", "This Idempotency-Key was already used with a different request");
    }

    @ExceptionHandler(RequestInProgressException.class)
    public ResponseEntity<ErrorResponse> handleRequestInProgress(RequestInProgressException e){
        return respond(HttpStatus.CONFLICT, "REQUEST_IN_PROGRESS", "A request with this Idempotency-Key is still being processed");
    }

    @ExceptionHandler(StorageUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleStorageUnavailable(StorageUnavailableException e){
        log.error("Document storage failure", e);
        return respond(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", "The file store is temporarily unavailable. Please try again.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception e){
        log.error("Unhandled exception", e);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred");
    }
}
