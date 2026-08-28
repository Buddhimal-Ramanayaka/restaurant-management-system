package com.rms.service;

import com.rms.exception.InvalidOrderStateException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * Live re-authentication for actions that require a manager's explicit sign-off
 * (manual billing discounts, voiding a PREPARING order) without issuing that
 * manager a full session token. Shared by BillingService and OrderService so the
 * credential-check and role-check logic exists in exactly one place.
 */
@Service
@RequiredArgsConstructor
public class ManagerAuthorizationService {

    private static final Set<String> AUTHORIZED_ROLES = Set.of("MANAGER", "ADMIN");

    private final AuthenticationManager authenticationManager;

    /** Throws InvalidOrderStateException if the credentials are missing/wrong or the user isn't a Manager/Admin. */
    public void requireManagerApproval(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new InvalidOrderStateException("Manager approval is required for this action");
        }
        try {
            var authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password));
            String role = authentication.getAuthorities().stream()
                    .findFirst()
                    .map(a -> a.getAuthority().replace("ROLE_", ""))
                    .orElse("");
            if (!AUTHORIZED_ROLES.contains(role)) {
                throw new InvalidOrderStateException("User " + username + " is not authorized to approve this action");
            }
        } catch (BadCredentialsException ex) {
            throw new InvalidOrderStateException("Manager credentials could not be verified");
        }
    }
}
