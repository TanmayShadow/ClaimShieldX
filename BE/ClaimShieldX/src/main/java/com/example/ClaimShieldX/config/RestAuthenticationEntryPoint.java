package com.example.ClaimShieldX.config;

import com.example.ClaimShieldX.common.utils.ErrorJson;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** Answers 401 for a protected endpoint reached without a usable token, telling the client why. */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException {
        Object reason = request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE);
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        if (JwtAuthenticationFilter.TOKEN_EXPIRED.equals(reason)) {
            ErrorJson.write(response, HttpStatus.UNAUTHORIZED, JwtAuthenticationFilter.TOKEN_EXPIRED, "The access token has expired");
        } else if (JwtAuthenticationFilter.TOKEN_INVALID.equals(reason)) {
            ErrorJson.write(response, HttpStatus.UNAUTHORIZED, JwtAuthenticationFilter.TOKEN_INVALID, "The access token is not valid");
        } else {
            ErrorJson.write(response, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required");
        }
    }
}
