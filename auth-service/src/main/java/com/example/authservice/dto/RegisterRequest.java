package com.example.authservice.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.Set;
import java.util.UUID;

@Data
public class RegisterRequest {

    @NotBlank(message = "username is required")
    @Size(min = 3, max = 50, message = "username must be 3-50 characters")
    private String username;

    @NotBlank(message = "email is required")
    @Email(message = "email must be valid")
    private String email;

    @NotBlank(message = "password is required")
    @Size(min = 8, message = "password must be at least 8 characters")
    private String password;

    @NotEmpty(message = "at least one role is required")
    private Set<String> roles;

    @NotNull(message = "tenantId is required")
    private UUID tenantId;
}
