package com.example.ClaimShieldX.exceptions;

import com.example.ClaimShieldX.common.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InsufficientAuthenticationException;

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
