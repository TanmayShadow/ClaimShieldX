package com.example.ClaimShieldX.auth.controller;

import com.example.ClaimShieldX.auth.dto.LoginRequest;
import com.example.ClaimShieldX.auth.dto.TokenResponse;
import com.example.ClaimShieldX.auth.service.AuthService;
import com.example.ClaimShieldX.common.utils.CookieManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;
    public AuthController(AuthService authService){
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest loginRequest){
        TokenResponse tokenResponse = authService.authenticate(loginRequest);
        ResponseCookie cookie = ResponseCookie.from(CookieManager.REFRESH_TOKEN_KEY,tokenResponse.refreshToken())
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/api/auth/refresh")
                .maxAge(7 * 24 * 60 * 60)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(tokenResponse);
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(HttpServletRequest request){
        String refreshToken = CookieManager.getCookie(request,CookieManager.REFRESH_TOKEN_KEY);
        String accessToken = request.getHeader("Authentication");
        TokenResponse tokenResponse = authService.refreshToken(accessToken,refreshToken);
        return ResponseEntity.ok()
                .body(tokenResponse);
    }


}
