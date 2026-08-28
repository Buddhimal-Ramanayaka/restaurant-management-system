package com.rms.service;

import com.rms.aspect.AuditableAction;
import com.rms.domain.User;
import com.rms.domain.enums.Role;
import com.rms.dto.request.CreateUserRequest;
import com.rms.dto.request.UpdateUserRequest;
import com.rms.dto.response.UserResponse;
import com.rms.exception.InvalidOrderStateException;
import com.rms.exception.ResourceNotFoundException;
import com.rms.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/** Admin "Manage Users & Roles" use case (Figure 2.1) - the one use case on the diagram that,
 *  until now, had no backend at all; every account in the system was schema.sql-seeded only. */
@Service
@RequiredArgsConstructor
public class UserManagementService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public List<UserResponse> findAll() {
        return userRepository.findAll().stream().map(UserResponse::from).collect(Collectors.toList());
    }

    @Transactional
    @AuditableAction("USER_CREATED")
    public UserResponse create(CreateUserRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new InvalidOrderStateException("Username '" + request.username() + "' is already taken");
        }
        Role role = parseRole(request.role());

        User user = User.builder()
                .username(request.username())
                .password(passwordEncoder.encode(request.password()))
                .fullName(request.fullName())
                .role(role)
                .isActive(true)
                .build();

        return UserResponse.from(userRepository.save(user));
    }

    /** actingUserId guards against an Admin locking themselves out via their own request. */
    @Transactional
    @AuditableAction("USER_UPDATED")
    public UserResponse update(Long userId, UpdateUserRequest request, Long actingUserId) {
        if (userId.equals(actingUserId)) {
            throw new InvalidOrderStateException("You cannot change your own role or active status");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        user.setFullName(request.fullName());
        user.setRole(parseRole(request.role()));
        user.setIsActive(request.isActive());

        return UserResponse.from(userRepository.save(user));
    }

    private Role parseRole(String value) {
        try {
            return Role.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new InvalidOrderStateException("Unknown role: " + value);
        }
    }
}
