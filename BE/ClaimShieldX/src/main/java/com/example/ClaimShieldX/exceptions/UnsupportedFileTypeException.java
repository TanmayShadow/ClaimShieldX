package com.example.ClaimShieldX.exceptions;

public class UnsupportedFileTypeException extends RuntimeException {
    public UnsupportedFileTypeException() {
        super("File type is not supported");
    }
}
