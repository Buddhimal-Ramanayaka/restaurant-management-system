package com.rms.dto.request;

import com.rms.domain.enums.OrderStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateOrderStatusRequest(
        @NotNull(message = "status is required") OrderStatus status,
        Long orderDetailId // when present, only this line moves; when null, the whole order moves
) {}
