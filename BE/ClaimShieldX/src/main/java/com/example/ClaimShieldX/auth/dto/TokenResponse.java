package com.example.ClaimShieldX.auth.dto;

public record TokenResponse(
        String accessToken,
        String refreshToken
) {}
