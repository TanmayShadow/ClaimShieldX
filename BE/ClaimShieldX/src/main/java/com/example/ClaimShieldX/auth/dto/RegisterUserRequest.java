package com.example.ClaimShieldX.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;


public record RegisterUserRequest(
        @NotBlank(message = "Email cannot be blank")
        @Email
        String email,
        @NotBlank(message = "Password cannot be blank")
        String password,
        @NotBlank(message = "Org. name cannot be blank")
        String organizationName
) {
}
