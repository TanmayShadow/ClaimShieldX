package com.example.ClaimShieldX.exceptions;

public class OrganizationNameAlreadyExistException extends RuntimeException {
    public OrganizationNameAlreadyExistException(String name) {
        super("Organization with name "+name+" already exist");
    }
}
