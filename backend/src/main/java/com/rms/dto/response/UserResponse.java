package com.rms.dto.response;

import com.rms.domain.User;

import java.time.LocalDateTime;

public record UserResponse(
        Long id,
        String username,
        String fullName,
        String role,
        Boolean isActive,
        LocalDateTime createdAt
) {
    public static UserResponse from(User u) {
        return new UserResponse(u.getId(), u.getUsername(), u.getFullName(), u.getRole().name(), u.getIsActive(), u.getCreatedAt());
    }
}
