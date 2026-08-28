package com.rms.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PurchaseOrderItemRequest(
        @NotNull Long ingredientId,
        @NotNull @DecimalMin(value = "0.001", message = "quantityOrdered must be positive") BigDecimal quantityOrdered,
        BigDecimal estimatedUnitCost
) {}
