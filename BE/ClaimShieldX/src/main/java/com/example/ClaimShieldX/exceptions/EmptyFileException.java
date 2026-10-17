package com.example.ClaimShieldX.exceptions;

public class EmptyFileException extends RuntimeException {
    public EmptyFileException() {
        super("Uploaded file is empty");
    }
}
