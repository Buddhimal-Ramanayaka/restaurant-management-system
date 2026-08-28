package com.rms.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * Pure observability - logs STOMP session connect/disconnect so an operator can see, from the
 * backend logs alone, when a Kitchen Display or POS terminal drops off the WebSocket (e.g. a
 * kitchen tablet losing wifi mid-service). Carries no business logic and never touches the
 * database; this is intentionally decoupled from OrderEventPublisher.
 */
@Component
@Slf4j
public class WebSocketEventListener {

    @EventListener
    public void handleSessionConnected(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String user = accessor.getUser() != null ? accessor.getUser().getName() : "anonymous";
        log.info("STOMP session connected: user={}, sessionId={}", user, accessor.getSessionId());
    }

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        log.info("STOMP session disconnected: sessionId={}, closeStatus={}", accessor.getSessionId(), event.getCloseStatus());
    }
}
