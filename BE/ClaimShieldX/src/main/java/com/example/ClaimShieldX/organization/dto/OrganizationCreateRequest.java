package com.example.ClaimShieldX.organization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OrganizationCreateRequest (
        @NotBlank(message = "Organization name cannot be blank")
        @Size(max = 255, message = "Name must be under 255 characters")
        String name
){ }
