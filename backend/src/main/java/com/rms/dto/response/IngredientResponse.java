package com.rms.dto.response;

import com.rms.domain.Ingredient;
import com.rms.domain.enums.UnitType;

import java.math.BigDecimal;

public record IngredientResponse(
        Long id,
        String name,
        BigDecimal currentStock,
        BigDecimal reorderLevel,
        UnitType unitType,
        BigDecimal averageUnitCost,
        Long preferredSupplierId,
        String preferredSupplierName
) {
    public static IngredientResponse from(Ingredient i) {
        return new IngredientResponse(
                i.getId(), i.getName(), i.getCurrentStock(), i.getReorderLevel(),
                i.getUnitType(), i.getAverageUnitCost(),
                i.getPreferredSupplier() != null ? i.getPreferredSupplier().getId() : null,
                i.getPreferredSupplier() != null ? i.getPreferredSupplier().getName() : null
        );
    }
}
