package com.example.ClaimShieldX.config;

import com.example.ClaimShieldX.common.utils.ErrorJson;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** Answers 403 when a signed-in user is not allowed to do something (URL-level rules). */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) throws IOException {
        ErrorJson.write(response, HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have permission to perform this action");
    }
}
