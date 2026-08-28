package com.rms.config;

import com.rms.security.CustomUserDetailsService;
import com.rms.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

import java.security.Principal;

/**
 * STOMP-over-WebSocket wiring for Module 2.4 (Kitchen KOT) and every other real-time
 * signal in the system (table status, low-stock alerts, order-ready pings back to a
 * waiter). One broker, several topics:
 *
 *   /topic/kitchen        - new/updated KOT tickets, broadcast to every Kitchen display
 *   /topic/tables         - table status transitions, broadcast to every Waiter/Cashier screen
 *   /topic/alerts/stock   - low-stock / out-of-stock warnings, broadcast to Admin + Manager
 *   /user/queue/order-ready - point-to-point: tells one specific waiter their order is up
 *
 * Clients publish inbound actions (kitchen tapping "start" / "ready") to /app/** which
 * routes to @MessageMapping methods in KitchenWebSocketController.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
@Slf4j
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(allowedOrigins.split(","))
                .withSockJS(); // graceful fallback for kitchen tablets on flaky wifi
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setSendTimeLimit(10_000).setSendBufferSizeLimit(512 * 1024);
    }

    /**
     * The HTTP handshake for /ws is permitAll (it has to be - the browser cannot attach
     * an Authorization header to a WebSocket upgrade request). Real authentication for
     * the socket happens here instead: the STOMP CONNECT frame carries the JWT as a
     * native header, and we populate the STOMP session Principal from it so that
     * @SendToUser / convertAndSendToUser addressing later resolves to the right waiter.
     *
     * FR-05 requires this to refuse unauthenticated connections outright, not merely skip
     * setting a Principal - message handlers like KitchenWebSocketController carry no
     * per-message authorization of their own (by design, they're thin relays onto the same
     * OrderService methods the REST endpoints call), so this CONNECT-time check is the ONLY
     * gate keeping an anonymous WebSocket client from publishing to /app/kitchen/line-status
     * and mutating order state. Throwing here causes Spring to send a STOMP ERROR frame and
     * close the session instead of silently completing the CONNECT.
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

                if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String token = accessor.getFirstNativeHeader("Authorization");
                    if (token == null || !token.startsWith("Bearer ")) {
                        log.debug("Rejecting STOMP CONNECT with no Bearer token");
                        throw new BadCredentialsException("Missing or malformed JWT on STOMP CONNECT");
                    }

                    String rawToken = token.substring(7);
                    try {
                        String username = jwtService.extractUsername(rawToken);
                        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                        if (!jwtService.isTokenValid(rawToken, userDetails.getUsername())) {
                            throw new BadCredentialsException("Expired or invalid JWT on STOMP CONNECT");
                        }
                        Principal principal = new UsernamePasswordAuthenticationToken(username, null, null);
                        accessor.setUser(principal);
                    } catch (BadCredentialsException ex) {
                        throw ex;
                    } catch (Exception ex) {
                        log.debug("Rejecting STOMP CONNECT with unusable JWT: {}", ex.getMessage());
                        throw new BadCredentialsException("Invalid JWT on STOMP CONNECT", ex);
                    }
                }
                return message;
            }
        });
    }
}
