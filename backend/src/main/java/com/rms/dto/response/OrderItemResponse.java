package com.rms.dto.response;

import com.rms.domain.OrderDetail;
import com.rms.domain.enums.OrderStatus;

public record OrderItemResponse(
        Long id,
        Long menuItemId,
        String menuItemName,
        Integer quantity,
        String specialNotes,
        OrderStatus lineStatus
) {
    public static OrderItemResponse from(OrderDetail detail) {
        return new OrderItemResponse(
                detail.getId(),
                detail.getMenuItem().getId(),
                detail.getMenuItem().getName(),
                detail.getQuantity(),
                detail.getSpecialNotes(),
                detail.getLineStatus()
        );
    }
}
