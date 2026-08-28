package com.rms.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CreateOrderRequest(
        @NotNull(message = "tableId is required") Long tableId,
        @NotEmpty(message = "order must contain at least one item")
        @Valid List<OrderItemRequest> items,
        String customerPhone // optional CRM lookup/registration, Module 2.10
) {}
