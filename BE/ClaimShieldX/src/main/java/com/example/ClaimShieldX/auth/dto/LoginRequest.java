package com.example.ClaimShieldX.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record   LoginRequest (
        @NotBlank(message = "Email cannot be blank")
        @Email(message = "Email is invalid")
        String email,

        @NotBlank(message = "Password cannot be blank")
        @Size(min = 6, message = "Password must be at least 6 characters")
        String password
){}
