package com.rms.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record GrnItemRequest(
        @NotNull Long ingredientId,
        @NotNull @DecimalMin(value = "0.001", message = "quantityReceived must be positive") BigDecimal quantityReceived,
        @NotNull @DecimalMin(value = "0.0001", message = "unitCost must be positive") BigDecimal unitCost
) {}
