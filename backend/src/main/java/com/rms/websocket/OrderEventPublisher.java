package com.rms.websocket;

import com.rms.domain.Order;
import com.rms.dto.response.*;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Single choke point for every outbound real-time signal in the system. Services never
 * touch SimpMessagingTemplate directly - they call one of these methods - so the set of
 * topics/queues the frontend needs to know about is fully enumerated in this one file.
 */
@Component
@RequiredArgsConstructor
public class OrderEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    /** Fired by OrderService right after a new ticket commits (Module 2.4: "New" lane). */
    public void publishNewTicket(Order order, String tableNumber) {
        messagingTemplate.convertAndSend(
                "/topic/kitchen",
                new KitchenTicketMessage(order.getId(), tableNumber, OrderResponse.from(order), "NEW_TICKET")
        );
    }

    /** Fired whenever a line (or the whole order) moves between PENDING/PREPARING/READY. */
    public void publishLineStatusChanged(Order order, String tableNumber) {
        messagingTemplate.convertAndSend(
                "/topic/kitchen",
                new KitchenTicketMessage(order.getId(), tableNumber, OrderResponse.from(order), "LINE_STATUS_CHANGED")
        );
    }

    public void publishOrderVoided(Order order, String tableNumber) {
        messagingTemplate.convertAndSend(
                "/topic/kitchen",
                new KitchenTicketMessage(order.getId(), tableNumber, OrderResponse.from(order), "ORDER_VOIDED")
        );
    }

    /**
     * Module 2.4: "Clicking complete must dynamically notify the origin waiter interface."
     * Routed point-to-point via Spring user destinations, which the WebSocketConfig
     * channel interceptor makes possible by attaching a Principal at CONNECT time.
     */
    public void notifyWaiterOrderReady(String waiterUsername, Long orderId, String tableNumber) {
        messagingTemplate.convertAndSendToUser(
                waiterUsername,
                "/queue/order-ready",
                new OrderReadyNotification(orderId, tableNumber)
        );
    }

    /** Module 2.6: broadcasts AVAILABLE/OCCUPIED/BILLED/CLEANING/RESERVED transitions to every terminal. */
    public void publishTableStatusChanged(TableResponse table) {
        messagingTemplate.convertAndSend("/topic/tables", new TableStatusMessage(table));
    }

    /** Module 2.2: low-stock / out-of-stock warning, consumed by Admin + Manager dashboards only. */
    public void publishStockAlert(StockAlertMessage alert) {
        messagingTemplate.convertAndSend("/topic/alerts/stock", alert);
    }

    /**
     * FR-09: the moment RecipeDeductionService auto-disables a menu item at zero stock, every
     * connected POS terminal needs to know without waiting for a page refresh - a waiter mid-order
     * on one terminal should stop being able to add a dish another terminal's order just sold out.
     */
    public void publishMenuItemAvailabilityChanged(MenuItemResponse item) {
        messagingTemplate.convertAndSend("/topic/menu", item);
    }
}
