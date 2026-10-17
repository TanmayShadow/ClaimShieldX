package com.example.ClaimShieldX.common.utils;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.time.Instant;

/**
 * Writes the standard error body from the security filter chain, where Spring MVC (and so the
 * exception handler) is not involved. Callers pass fixed strings, never user input, so nothing is escaped.
 */
public final class ErrorJson {
    private ErrorJson() {
    }

    public static void write(HttpServletResponse response, HttpStatus status, String code, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"timestamp\":\"" + Instant.now() + "\",\"status\":" + status.value()
                + ",\"error\":\"" + status.getReasonPhrase() + "\",\"code\":\"" + code
                + "\",\"message\":\"" + message + "\"}");
    }
}
