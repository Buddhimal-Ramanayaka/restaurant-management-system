package com.rms.config;

import com.rms.security.CustomUserDetailsService;
import com.rms.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Stateless JWT security. Route guards here are the backend half of the five-role
 * RBAC matrix described in Module 2.1 - the React route guards on the frontend are
 * a UX convenience only, this is the actual enforcement boundary.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    /** NFR-06 requires a minimum BCrypt work factor of 12; Spring's no-arg constructor defaults to 10. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // stateless JWT API, no cookies to protect against CSRF
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // Explicit rather than relying on Spring Security's default fallback, which
            // is ambiguous once no formLogin()/httpBasic() is configured and can produce
            // 403 for a request that never authenticated at all - NFR-08 requires that
            // specific case to be 401, with 403 reserved for authenticated-but-wrong-role.
            .exceptionHandling(ex -> ex
                    .authenticationEntryPoint(unauthorizedEntryPoint())
                    .accessDeniedHandler(accessDeniedHandler()))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/ws/**").permitAll() // STOMP handshake; per-message auth happens in the channel interceptor
                .requestMatchers(HttpMethod.GET, "/api/menu-items/**").hasAnyRole("ADMIN", "MANAGER", "WAITER", "CASHIER", "KITCHEN")
                .requestMatchers("/api/menu-items/**").hasAnyRole("ADMIN", "MANAGER")
                // Kitchen needs the plain ingredient list to populate the "Log Waste" ingredient
                // picker, but not the broader /api/ingredients/** surface (create, stock-correction) -
                // same narrowing pattern as the GET /api/menu-items/** rule just above.
                .requestMatchers(HttpMethod.GET, "/api/ingredients").hasAnyRole("ADMIN", "MANAGER", "KITCHEN")
                .requestMatchers("/api/ingredients/**").hasAnyRole("ADMIN", "MANAGER")
                .requestMatchers("/api/waste-logs/**").hasAnyRole("ADMIN", "MANAGER", "KITCHEN")
                .requestMatchers(HttpMethod.POST, "/api/orders").hasAnyRole("WAITER", "MANAGER", "ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/api/orders/*/status").hasAnyRole("KITCHEN", "WAITER", "MANAGER", "ADMIN", "CASHIER")
                .requestMatchers("/api/orders/**").hasAnyRole("WAITER", "KITCHEN", "CASHIER", "MANAGER", "ADMIN")
                .requestMatchers("/api/tables/**").hasAnyRole("WAITER", "MANAGER", "ADMIN", "CASHIER")
                .requestMatchers("/api/reservations/**").hasAnyRole("WAITER", "MANAGER", "ADMIN")
                .requestMatchers("/api/customers/**").hasAnyRole("WAITER", "MANAGER", "ADMIN")
                .requestMatchers("/api/promotions/**").hasAnyRole("WAITER", "CASHIER", "MANAGER", "ADMIN")
                // FR-21: rates are Admin-configurable specifically - PUT is narrower than the
                // GET rule below it, and must be declared first for that narrowing to apply.
                .requestMatchers(HttpMethod.PUT, "/api/settings/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/settings/**").hasAnyRole("WAITER", "CASHIER", "MANAGER", "ADMIN")
                // Shift review (Module 2.8 Manager use case) is Manager/Admin-only oversight of
                // OTHER cashiers' financials - must be checked before the general /api/billing/**
                // rule below, which is what a cashier legitimately needs for their own shift.
                .requestMatchers(HttpMethod.GET, "/api/billing/shifts").hasAnyRole("MANAGER", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/billing/shifts/*/review").hasAnyRole("MANAGER", "ADMIN")
                .requestMatchers("/api/billing/**").hasAnyRole("CASHIER", "MANAGER", "ADMIN")
                .requestMatchers("/api/grn/**").hasAnyRole("MANAGER", "ADMIN")
                // Admin "Manage Suppliers" (Figure 2.1): creating/editing a supplier is
                // Admin-only, checked before the broader GET-and-everything-else rule below
                // that Manager also needs for the GRN/PO supplier picker.
                .requestMatchers(HttpMethod.POST, "/api/suppliers/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/suppliers/**").hasRole("ADMIN")
                .requestMatchers("/api/suppliers/**").hasAnyRole("MANAGER", "ADMIN")
                .requestMatchers("/api/purchase-orders/**").hasAnyRole("MANAGER", "ADMIN")
                .requestMatchers("/api/analytics/**").hasAnyRole("ADMIN", "MANAGER")
                // Admin "Manage Users & Roles" (Figure 2.1).
                .requestMatchers("/api/users/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Fired only for genuinely unauthenticated requests (no token, expired token,
     * malformed token) - never for an authenticated user hitting an endpoint outside
     * their role, which Spring Security routes to the default AccessDeniedHandler
     * (403) instead, since that path never throws AuthenticationException.
     */
    @Bean
    public AuthenticationEntryPoint unauthorizedEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Authentication required.\"}");
        };
    }

    /**
     * NFR-08 requires 403 for an authenticated user hitting an endpoint outside their role,
     * reserving 401 for genuinely unauthenticated requests. Explicit bean rather than relying
     * on Spring Security's default AccessDeniedHandlerImpl, since without a WWW-Authenticate
     * exchange in play the default ExceptionTranslationFilter routing can otherwise fall back
     * to re-triggering the AuthenticationEntryPoint for a role mismatch, collapsing both cases
     * into 401.
     */
    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            response.setStatus(jakarta.servlet.http.HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"status\":403,\"error\":\"Forbidden\",\"message\":\"You do not have permission to access this resource.\"}");
        };
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(allowedOrigins.split(",")));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
