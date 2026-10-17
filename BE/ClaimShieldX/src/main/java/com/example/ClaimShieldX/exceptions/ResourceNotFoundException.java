package com.example.ClaimShieldX.exceptions;

/** The resource does not exist, or exists in another organization; the two are deliberately indistinguishable. */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
