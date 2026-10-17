package com.example.ClaimShieldX.auth.controller;

import com.example.ClaimShieldX.auth.dto.LoginRequest;
import com.example.ClaimShieldX.auth.dto.RefreshTokenRequest;
import com.example.ClaimShieldX.auth.dto.TokenResponse;
import com.example.ClaimShieldX.auth.service.AuthService;
import com.example.ClaimShieldX.auth.service.RefreshTokenService;
import com.example.ClaimShieldX.common.utils.CookieManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;

    public AuthController(AuthService authService, RefreshTokenService refreshTokenService){
        this.authService = authService;
        this.refreshTokenService = refreshTokenService;
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest loginRequest){
        return withRefreshCookie(authService.authenticate(loginRequest));
    }

    /** Works without an access token; the refresh token (cookie, or body for non-browser clients) is the credential. */
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(HttpServletRequest request, @Valid @RequestBody(required = false) RefreshTokenRequest body){
        return withRefreshCookie(authService.refresh(presentedRefreshToken(request, body)));
    }

    /** Always 204, even for an unknown token, so it reveals nothing. Works with an expired access token. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, @Valid @RequestBody(required = false) RefreshTokenRequest body){
        authService.logout(presentedRefreshToken(request, body));
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, CookieManager.clearedRefreshCookie().toString())
                .build();
    }

    private String presentedRefreshToken(HttpServletRequest request, RefreshTokenRequest body){
        String fromCookie = CookieManager.getCookie(request, CookieManager.REFRESH_TOKEN_KEY);
        if(fromCookie != null && !fromCookie.isBlank())
            return fromCookie;
        return body == null ? null : body.refreshToken();
    }

    private ResponseEntity<TokenResponse> withRefreshCookie(TokenResponse tokenResponse){
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, CookieManager.refreshCookie(tokenResponse.refreshToken(), refreshTokenService.getTtlSeconds()).toString())
                .body(tokenResponse);
    }
}
