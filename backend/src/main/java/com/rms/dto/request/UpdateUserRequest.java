package com.rms.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Role re-assignment and activate/deactivate - deliberately does not carry a password field;
 *  password reset is a separate, deliberately unbuilt concern (out of the diagram's scope). */
public record UpdateUserRequest(
        @NotBlank String fullName,
        @NotNull String role,
        @NotNull Boolean isActive
) {}
