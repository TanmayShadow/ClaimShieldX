package com.example.ClaimShieldX.exceptions;

public class RequestInProgressException extends RuntimeException {
    public RequestInProgressException() {
        super("A request with this idempotency key is still being processed");
    }
}
