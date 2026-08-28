package com.rms.integration;

import com.rms.domain.User;
import com.rms.domain.enums.Role;
import com.rms.dto.request.CreateIngredientRequest;
import com.rms.dto.response.AuthResponse;
import com.rms.domain.enums.UnitType;
import com.rms.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IT-04 and IT-05: authorization and authentication enforcement. These are the
 * black-box confirmation that SecurityConfig's route matcher table is wired
 * correctly and that JwtAuthenticationFilter actually rejects requests it
 * should reject - unit-testing SecurityConfig in isolation would not catch a
 * misconfigured matcher order or an accidentally-permissive wildcard.
 */
class SecurityIntegrationTest extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private String waiterJwt;

    @BeforeEach
    void seedData() {
        userRepository.save(User.builder()
                .username("it04-waiter").password(passwordEncoder.encode("password123"))
                .role(Role.WAITER).isActive(true).fullName("Test Waiter").build());

        AuthResponse auth = restTemplate.postForObject(
                "/api/auth/login",
                new com.rms.dto.request.LoginRequest("it04-waiter", "password123"),
                AuthResponse.class);
        waiterJwt = auth.token();
    }

    @Test
    @DisplayName("IT-04: A Waiter token calling an Admin/Manager-only endpoint receives 403 Forbidden")
    void waiterToken_callingManagerOnlyEndpoint_forbidden() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(waiterJwt);

        CreateIngredientRequest request = new CreateIngredientRequest(
                "IT04-Forbidden-Ingredient", BigDecimal.TEN, BigDecimal.ONE, UnitType.KG, null);

        var response = restTemplate.exchange(
                "/api/ingredients", HttpMethod.POST, new HttpEntity<>(request, headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("IT-05: A request with no Authorization header receives 401 Unauthorized")
    void noToken_callingProtectedEndpoint_unauthorized() {
        var response = restTemplate.getForEntity("/api/orders/active", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("A request with a garbage/malformed token is treated as unauthenticated, not a 500 error")
    void malformedToken_treatedAsUnauthenticated() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer this.is.not.a.valid.jwt");

        var response = restTemplate.exchange(
                "/api/orders/active", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }
}
