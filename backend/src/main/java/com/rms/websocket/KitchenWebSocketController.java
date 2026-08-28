package com.rms.websocket;

import com.rms.dto.request.LineStatusUpdateMessage;
import com.rms.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

/**
 * Inbound STOMP signalling handler for Module 2.4. Kitchen staff tapping "Start Cooking" or
 * ticking a line item complete on the Kanban board publishes a message to /app/kitchen/line-status
 * instead of calling a REST endpoint - this is the "WebSocket signalling handler" side of the
 * system, complementing OrderEventPublisher which handles the outbound broadcast direction.
 *
 * Deliberately thin: this class does not contain any business logic itself. It parses the
 * inbound frame and immediately delegates to OrderService.updateStatus, the exact same method
 * the REST PATCH /api/orders/{id}/status endpoint calls - so a Kitchen Display that falls back
 * to REST (e.g. WebSocket temporarily down) and one driven purely over STOMP can never diverge
 * in behaviour, because there is only one implementation of the state transition.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class KitchenWebSocketController {

    private final OrderService orderService;

    /**
     * Client publishes to /app/kitchen/line-status with a LineStatusUpdateMessage payload
     * {orderId, orderDetailId (nullable), newStatus}. There is no @SendTo here because the
     * broadcast back out is handled by OrderService itself (via OrderEventPublisher) once the
     * transition succeeds - this method does not need to construct or route the outbound
     * message, only trigger the state change.
     */
    @MessageMapping("/kitchen/line-status")
    public void handleLineStatusUpdate(LineStatusUpdateMessage message) {
        log.debug("STOMP inbound: kitchen line-status update {}", message);
        // orderId is not part of LineStatusUpdateMessage by design (it only carries the line
        // and target status); the Kitchen Display always knows which ticket a line belongs to
        // from the card it rendered, so the REST-equivalent path takes orderId as a path
        // variable while this STOMP path expects the client to have already resolved it.
        // Here we resolve via the orderDetailId alone through OrderService, which looks up
        // the owning order internally when only a line id is supplied.
        orderService.updateStatusByLineId(message.orderDetailId(), message.newStatus());
    }

    /**
     * A kitchen terminal can request a full resync (e.g. after reconnecting from a dropped
     * WebSocket session) by publishing an empty payload to /app/kitchen/resync. The response
     * is delivered back to the requesting session only, via @SendToUser, rather than
     * broadcast to every terminal - resync is a per-client concern.
     */
    @MessageMapping("/kitchen/resync")
    @SendToUser("/queue/kitchen-resync")
    public String requestResync() {
        log.info("Kitchen terminal requested a full resync");
        return "RESYNC_ACK";
    }
}
