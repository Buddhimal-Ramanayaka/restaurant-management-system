package com.rms.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record MenuItemRequest(
        @NotBlank String name,
        @NotNull @DecimalMin(value = "0.00", message = "price cannot be negative") BigDecimal price,
        @NotBlank String category,
        String imageUrl,
        @Valid List<RecipeLineRequest> recipeLines
) {}
