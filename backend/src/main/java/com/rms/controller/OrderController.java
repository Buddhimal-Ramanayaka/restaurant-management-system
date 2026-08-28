package com.rms.controller;

import com.rms.domain.Order;
import com.rms.dto.request.CreateOrderRequest;
import com.rms.dto.request.UpdateOrderStatusRequest;
import com.rms.dto.request.VoidOrderRequest;
import com.rms.dto.response.OrderResponse;
import com.rms.security.UserPrincipal;
import com.rms.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * The single entry point for Module 2.2 SUBMITTED trigger. See OrderService.submitOrder
     * for the transactional boundary that ties table locking, recipe deduction, and the
     * WebSocket broadcast together.
     */
    @PostMapping
    public ResponseEntity<OrderResponse> submitOrder(
            @Valid @RequestBody CreateOrderRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        Order order = orderService.submitOrder(request, principal);
        return ResponseEntity.ok(OrderResponse.from(order));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<OrderResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateOrderStatusRequest request
    ) {
        Order order = orderService.updateStatus(id, request.status(), request.orderDetailId());
        return ResponseEntity.ok(OrderResponse.from(order));
    }

    @PostMapping("/{id}/void")
    public ResponseEntity<OrderResponse> voidOrder(
            @PathVariable Long id,
            @RequestBody(required = false) VoidOrderRequest request
    ) {
        VoidOrderRequest body = request != null ? request : new VoidOrderRequest(null, null);
        Order order = orderService.voidOrder(id, body.managerUsername(), body.managerPassword());
        return ResponseEntity.ok(OrderResponse.from(order));
    }

    /** Backing endpoint for the Kitchen Display initial load (subsequent updates arrive over STOMP). */
    @GetMapping("/active")
    public ResponseEntity<List<OrderResponse>> findActiveForKitchen() {
        List<OrderResponse> orders = orderService.findActiveForKitchen().stream()
                .map(OrderResponse::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(orders);
    }

    /** Backing endpoint for the Cashier Billing Terminal queue. */
    @GetMapping("/awaiting-billing")
    public ResponseEntity<List<OrderResponse>> findAwaitingBilling() {
        List<OrderResponse> orders = orderService.findAwaitingBilling().stream()
                .map(OrderResponse::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(orders);
    }
}
