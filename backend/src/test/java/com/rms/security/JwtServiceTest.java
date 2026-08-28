package com.rms.security;

import com.rms.domain.User;
import com.rms.domain.enums.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for JwtService. Mirrors UT-15 and UT-16 from the dissertation
 * Chapter 5 evaluation table.
 *
 * A fixed, fake 256-bit secret is used here purely for token signing/verification
 * in-memory - it is never the real app.jwt.secret and never touches any
 * configuration file.
 */
class JwtServiceTest {

    private static final String TEST_SECRET = "test-secret-key-with-at-least-256-bits-of-entropy!!";

    private User sampleUser() {
        return User.builder()
                .id(7L).username("kamal").password("hashed").role(Role.WAITER).isActive(true)
                .build();
    }

    @Test
    @DisplayName("UT-15: A freshly generated token validates successfully against its own username")
    void generateAndValidateToken_succeeds() {
        JwtService jwtService = new JwtService(TEST_SECRET, 8 * 60 * 60 * 1000L); // 8 hours
        UserPrincipal principal = new UserPrincipal(sampleUser());

        String token = jwtService.generateToken(principal);

        assertThat(token).isNotBlank();
        assertThat(jwtService.extractUsername(token)).isEqualTo("kamal");
        assertThat(jwtService.isTokenValid(token, "kamal")).isTrue();
    }

    @Test
    @DisplayName("UT-16: An expired token fails validation even with the correct username")
    void expiredToken_isRejected() {
        // Negative expiration means the token's exp claim is already in the past at the
        // moment it is issued - deterministic, no Thread.sleep flakiness required.
        JwtService jwtService = new JwtService(TEST_SECRET, -10_000L);
        UserPrincipal principal = new UserPrincipal(sampleUser());

        String token = jwtService.generateToken(principal);

        assertThat(jwtService.isTokenValid(token, "kamal")).isFalse();
    }

    @Test
    @DisplayName("A token issued for one user does not validate against a different username")
    void tokenValidation_wrongUsername_rejected() {
        JwtService jwtService = new JwtService(TEST_SECRET, 8 * 60 * 60 * 1000L);
        UserPrincipal principal = new UserPrincipal(sampleUser());

        String token = jwtService.generateToken(principal);

        assertThat(jwtService.isTokenValid(token, "someone-else")).isFalse();
    }
}
