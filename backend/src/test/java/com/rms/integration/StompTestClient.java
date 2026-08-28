package com.rms.integration;

import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Thin wrapper around Spring's WebSocketStompClient for integration tests that need
 * to assert a real STOMP broadcast was received (IT-06, IT-08, IT-09) - the same
 * client-side stack the actual React frontend uses (SockJS + STOMP), just driven
 * from JUnit instead of a browser.
 */
public class StompTestClient {

    private final WebSocketStompClient stompClient;

    public StompTestClient() {
        SockJsClient sockJsClient = new SockJsClient(
                List.of(new WebSocketTransport(new StandardWebSocketClient())));
        this.stompClient = new WebSocketStompClient(sockJsClient);
        this.stompClient.setMessageConverter(new MappingJackson2MessageConverter());
    }

    public StompSession connect(String wsUrl, String jwt) throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + jwt);

        CompletableFuture<StompSession> future = new CompletableFuture<>();
        stompClient.connectAsync(wsUrl, (WebSocketHttpHeaders) null, connectHeaders, new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                future.complete(session);
            }

            @Override
            public void handleException(StompSession session, org.springframework.messaging.simp.stomp.StompCommand command,
                                         StompHeaders headers, byte[] payload, Throwable exception) {
                future.completeExceptionally(exception);
            }

            @Override
            public void handleTransportError(StompSession session, Throwable exception) {
                future.completeExceptionally(exception);
            }
        });

        return future.get(10, java.util.concurrent.TimeUnit.SECONDS);
    }
}
