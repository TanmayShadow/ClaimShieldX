package com.example.ClaimShieldX.exceptions;

import com.example.ClaimShieldX.common.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTests {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private void assertBody(ResponseEntity<ErrorResponse> response, HttpStatus status, String code) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getBody().getStatus()).isEqualTo(status.value());
        assertThat(response.getBody().getError()).isEqualTo(status.getReasonPhrase());
        assertThat(response.getBody().getCode()).isEqualTo(code);
        assertThat(response.getBody().getMessage()).isNotBlank();
        assertThat(response.getBody().getTimestamp()).isNotNull();
    }

    @Test
    void unexpectedErrorDoesNotLeakTheExceptionMessage() {
        ResponseEntity<ErrorResponse> response = handler.handleGenericException(
                new RuntimeException("select * from claims where internal_id='secret-detail'"));

        assertBody(response, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR");
        assertThat(response.getBody().getMessage()).isEqualTo("An unexpected error occurred");
        assertThat(response.getBody().getMessage()).doesNotContain("secret-detail");
    }

    @Test
    void everyHandlerAnswersWithItsStatusAndAStableCode() {
        assertBody(handler.handleEmailAlreadyExistException(new EmailAlreadyExistException("x")), HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED");
        assertBody(handler.handleOrganizationNameAlreadyExistException(new OrganizationNameAlreadyExistException("x")), HttpStatus.CONFLICT, "ORGANIZATION_ALREADY_EXISTS");
        assertBody(handler.handleOrganizationNotFoundException(new OrganizationNotFoundException("x")), HttpStatus.NOT_FOUND, "ORGANIZATION_NOT_FOUND");
        assertBody(handler.handleRefreshTokenExpiredException(new RefreshTokenExpiredException("x")), HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_EXPIRED");
        assertBody(handler.handleRefreshTokenInvalidException(new RefreshTokenInvalidException("x")), HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_INVALID");
        assertBody(handler.handleBadCredentials(new BadCredentialsException("x")), HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
        assertBody(handler.handleAuthenticationException(new InsufficientAuthenticationException("x")), HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
        assertBody(handler.handleAccessDenied(new AccessDeniedException("x")), HttpStatus.FORBIDDEN, "FORBIDDEN");
        assertBody(handler.handleDuplicateClaimException(new DuplicateClaimException("x")), HttpStatus.CONFLICT, "DUPLICATE_CLAIM");
    }

    @Test
    void uploadAndIdempotencyHandlersAnswerWithTheContractedStatusAndCode() {
        assertBody(handler.handleResourceNotFound(new ResourceNotFoundException("x")), HttpStatus.NOT_FOUND, "NOT_FOUND");
        assertBody(handler.handleEmptyFile(new EmptyFileException()), HttpStatus.BAD_REQUEST, "EMPTY_FILE");
        assertBody(handler.handleUnsupportedFileType(new UnsupportedFileTypeException()), HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE");
        assertBody(handler.handleDocumentLimit(new DocumentLimitReachedException()), HttpStatus.CONFLICT, "DOCUMENT_LIMIT_REACHED");
        assertBody(handler.handleInvalidStateTransition(new InvalidStateTransitionException("x")), HttpStatus.CONFLICT, "INVALID_STATE_TRANSITION");
        assertBody(handler.handleIdempotencyKeyReused(new IdempotencyKeyReusedException()), HttpStatus.UNPROCESSABLE_ENTITY, "IDEMPOTENCY_KEY_REUSED");
        assertBody(handler.handleRequestInProgress(new RequestInProgressException()), HttpStatus.CONFLICT, "REQUEST_IN_PROGRESS");
        assertBody(handler.handleUploadTooLarge(new MaxUploadSizeExceededException(1024)), HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE");
        assertBody(handler.handleMultipartProblem(new MultipartException("broken")), HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST");
        assertBody(handler.handleExtractionNotSupported(new ExtractionNotSupportedException(com.example.ClaimShieldX.documents.model.DocumentType.CLAIM_FORM)),
                HttpStatus.UNPROCESSABLE_ENTITY, "EXTRACTION_NOT_SUPPORTED");
    }

    @Test
    void aDuplicateDocumentNamesTheExistingOneAndAValidationErrorListsItsFields() {
        java.util.UUID existing = java.util.UUID.randomUUID();

        ResponseEntity<ErrorResponse> duplicate = handler.handleDuplicateDocument(new DuplicateDocumentException(existing));
        assertBody(duplicate, HttpStatus.CONFLICT, "DUPLICATE_DOCUMENT");
        assertThat(duplicate.getBody().getDetails()).containsEntry("existingDocumentId", existing.toString());

        ResponseEntity<ErrorResponse> invalid = handler.handleRequestValidation(RequestValidationException.of("file", "could not be read"));
        assertBody(invalid, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
        assertThat(invalid.getBody().getErrors()).extracting(ErrorResponse.FieldIssue::field).containsExactly("file");
    }

    @Test
    void aStorageFailureIsA503ThatNeverRevealsTheCause() {
        ResponseEntity<ErrorResponse> response = handler.handleStorageUnavailable(
                new StorageUnavailableException("cannot write /var/secret/claim-docs", new java.io.IOException("permission denied")));

        assertBody(response, HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE");
        assertThat(response.getBody().getMessage()).doesNotContain("secret").doesNotContain("permission");
    }

    @Test
    void clientMessagesNeverContainTheInternalExceptionText() {
        ResponseEntity<ErrorResponse> response = handler.handleBadCredentials(new BadCredentialsException("password hash mismatch for user 42"));

        assertThat(response.getBody().getMessage()).doesNotContain("hash").doesNotContain("42");
    }

    @Test
    void errorResponseExposesItsFields() {
        ErrorResponse body = new ErrorResponse(HttpStatus.CONFLICT, "SOME_CODE", "message");

        assertThat(body.getStatus()).isEqualTo(409);
        assertThat(body.getError()).isEqualTo("Conflict");
        assertThat(body.getCode()).isEqualTo("SOME_CODE");
        assertThat(body.getMessage()).isEqualTo("message");
        assertThat(body.getTimestamp()).isNotNull();
        assertThat(body.getErrors()).isNull();
    }
}
