package com.finsight.api.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @Email(message = "Enter a valid email address")
    @NotBlank(message = "Email is required")
    @Size(max = 255, message = "Email must be at most 255 characters")
    String email,

    @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
    @NotBlank(message = "Password is required")
    String password,

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must be at most 100 characters")
    String displayName
) {}
