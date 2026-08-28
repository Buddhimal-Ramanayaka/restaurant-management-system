package com.rms.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record OrderItemRequest(
        @NotNull(message = "menuItemId is required") Long menuItemId,
        @Positive(message = "quantity must be greater than zero") Integer quantity,
        String specialNotes
) {}
