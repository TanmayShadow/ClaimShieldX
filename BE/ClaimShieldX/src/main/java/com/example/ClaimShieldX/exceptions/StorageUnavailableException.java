package com.example.ClaimShieldX.exceptions;

/** The file store could not complete an operation. The cause is logged server-side and never sent to the client. */
public class StorageUnavailableException extends RuntimeException {
    public StorageUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
