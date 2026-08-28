package com.rms.dto.request;

import com.rms.domain.enums.UnitType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateIngredientRequest(
        @NotBlank String name,
        @NotNull @DecimalMin(value = "0", message = "currentStock cannot be negative") BigDecimal currentStock,
        @NotNull @DecimalMin(value = "0", message = "reorderLevel cannot be negative") BigDecimal reorderLevel,
        @NotNull UnitType unitType,
        Long preferredSupplierId
) {}
