package com.rms.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Runs once per request: pulls the Bearer token out of the Authorization header,
 * validates it, and - if valid - populates the SecurityContext so downstream
 * @PreAuthorize checks and controller @AuthenticationPrincipal injection work.
 * Stateless by design: nothing here touches an HttpSession.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        // A malformed token (garbage string, wrong signature, truncated) or a token
        // whose subject no longer exists in the database (user deleted after the
        // token was issued) must fail CLOSED as "request continues unauthenticated" -
        // not bubble up as an uncaught exception. Left unguarded, either case would
        // surface to the client as a 500 Internal Server Error instead of the 401/403
        // Spring Security's own access-control layer produces for an unauthenticated
        // or under-privileged request.
        try {
            String username = jwtService.extractUsername(token);

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                if (jwtService.isTokenValid(token, userDetails.getUsername())) {
                    var authToken = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (Exception ex) {
            log.debug("Rejecting request with unusable JWT: {}", ex.getMessage());
            // Deliberately no rethrow - fall through with no authentication set, so
            // downstream authorization rules treat this exactly like a missing token.
        }

        filterChain.doFilter(request, response);
    }
}
