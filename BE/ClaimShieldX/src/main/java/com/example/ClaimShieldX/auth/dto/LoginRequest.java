package com.example.ClaimShieldX.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Login does not enforce the password policy: that belongs to registration, and a wrong password must read as wrong credentials.
public record   LoginRequest (
        @NotBlank(message = "Email cannot be blank")
        @Email(message = "Email is invalid")
        String email,
        @NotBlank(message = "Password cannot be blank")
        @Size(max = 128, message = "Password is too long")
        String password
){}
