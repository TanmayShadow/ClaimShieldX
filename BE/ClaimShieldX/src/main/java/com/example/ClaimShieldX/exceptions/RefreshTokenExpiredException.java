package com.example.ClaimShieldX.exceptions;

public class RefreshTokenExpiredException extends RuntimeException {
    public RefreshTokenExpiredException(String message) {
        super(message);
    }
}
