package com.rms.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record RecipeLineRequest(
        @NotNull Long ingredientId,
        @NotNull @DecimalMin(value = "0.001", message = "quantityRequired must be positive") BigDecimal quantityRequired
) {}
