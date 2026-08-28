package com.rms.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Admin "Manage Users & Roles" use case (Figure 2.1). */
public record CreateUserRequest(
        @NotBlank String username,
        @NotBlank String password,
        @NotBlank String fullName,
        @NotNull String role
) {}
