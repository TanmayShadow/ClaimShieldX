package com.example.ClaimShieldX.exceptions;

import com.example.ClaimShieldX.common.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTests {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void unexpectedErrorDoesNotLeakTheExceptionMessage() {
        ResponseEntity<ErrorResponse> response = handler.handleGenericException(
                new RuntimeException("select * from claims where internal_id='secret-detail'"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getMessage()).isEqualTo("An unexpected error occurred");
        assertThat(response.getBody().getMessage()).doesNotContain("secret-detail");
    }

    @Test
    void errorResponseExposesItsFields() {
        ErrorResponse body = new ErrorResponse(409, "Conflict", "message");

        assertThat(body.getStatus()).isEqualTo(409);
        assertThat(body.getError()).isEqualTo("Conflict");
        assertThat(body.getMessage()).isEqualTo("message");
        assertThat(body.getTimestamp()).isNotNull();
        assertThat(body.getErrors()).isNull();
    }
}
