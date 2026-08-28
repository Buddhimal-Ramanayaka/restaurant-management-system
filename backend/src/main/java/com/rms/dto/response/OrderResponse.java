package com.rms.dto.response;

import com.rms.domain.Order;
import com.rms.domain.enums.OrderStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

public record OrderResponse(
        Long id,
        Long tableId,
        Long waiterId,
        String waiterUsername,
        OrderStatus status,
        List<OrderItemResponse> items,
        LocalDateTime createdAt,
        LocalDateTime preparingStartedAt
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getTableId(),
                order.getWaiter().getId(),
                order.getWaiter().getUsername(),
                order.getStatus(),
                order.getItems().stream().map(OrderItemResponse::from).collect(Collectors.toList()),
                order.getCreatedAt(),
                order.getPreparingStartedAt()
        );
    }
}
