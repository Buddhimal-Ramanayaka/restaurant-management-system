package com.rms.dto.response;

import com.rms.domain.Recipe;

import java.math.BigDecimal;

public record RecipeLineResponse(Long ingredientId, String ingredientName, BigDecimal quantityRequired) {
    public static RecipeLineResponse from(Recipe r) {
        return new RecipeLineResponse(r.getIngredient().getId(), r.getIngredient().getName(), r.getQuantityRequired());
    }
}
