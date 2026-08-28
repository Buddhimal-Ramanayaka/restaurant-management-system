package com.rms.dto.request;

import com.rms.domain.enums.OrderStatus;

/** Inbound STOMP payload published by a Kitchen client to /app/kitchen/line-status. */
public record LineStatusUpdateMessage(
        Long orderDetailId,
        OrderStatus newStatus
) {}
