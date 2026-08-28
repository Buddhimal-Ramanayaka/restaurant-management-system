package com.rms.dto.response;

/**
 * Payload broadcast on /topic/kitchen every time an order is created or a line status
 * changes. Deliberately flat and self-contained (not just "here is an order id, go
 * fetch it") so the Kitchen display can render the full ticket straight off the socket
 * message with zero follow-up REST calls - that is what "no unnecessary refreshes"
 * means in practice for this screen.
 */
public record KitchenTicketMessage(
        Long orderId,
        String tableNumber,
        OrderResponse order,
        String eventType // NEW_TICKET, LINE_STATUS_CHANGED, ORDER_VOIDED
) {}
