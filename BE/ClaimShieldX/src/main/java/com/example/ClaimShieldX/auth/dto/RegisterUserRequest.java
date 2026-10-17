package com.example.ClaimShieldX.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.nio.charset.StandardCharsets;

public record RegisterUserRequest(
        @NotBlank(message = "Email cannot be blank")
        @Email
        @Size(max = 255, message = "Email cannot exceed 255 characters")
        String email,
        @NotBlank(message = "Password cannot be blank")
        @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
        String password,
        @NotBlank(message = "Org. name cannot be blank")
        @Size(max = 255, message = "Org. name cannot exceed 255 characters")
        String organizationName
) {
    // BCrypt only reads the first 72 bytes, and multi-byte characters can exceed that within 72 characters.
    @JsonIgnore
    @AssertTrue(message = "Password must be at most 72 bytes")
    public boolean isPasswordWithinBcryptLimit() {
        return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
