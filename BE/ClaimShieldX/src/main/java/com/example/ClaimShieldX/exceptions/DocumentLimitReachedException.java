package com.example.ClaimShieldX.exceptions;

public class DocumentLimitReachedException extends RuntimeException {
    public DocumentLimitReachedException() {
        super("The claim has reached its document limit");
    }
}
