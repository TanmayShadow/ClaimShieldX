package com.example.ClaimShieldX.exceptions;

public class IdempotencyKeyReusedException extends RuntimeException {
    public IdempotencyKeyReusedException() {
        super("Idempotency key was used with a different request");
    }
}
