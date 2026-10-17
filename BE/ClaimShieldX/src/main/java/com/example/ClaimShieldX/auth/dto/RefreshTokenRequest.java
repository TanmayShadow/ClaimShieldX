package com.example.ClaimShieldX.auth.dto;

import jakarta.validation.constraints.Size;

// Optional body for clients that cannot use the cookie; the cookie wins when both are present.
public record RefreshTokenRequest(
        @Size(max = 512, message = "Refresh token is too long")
        String refreshToken
) {}
