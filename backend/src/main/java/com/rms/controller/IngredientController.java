package com.rms.controller;

import com.rms.domain.Ingredient;
import com.rms.dto.request.CreateIngredientRequest;
import com.rms.dto.request.StockCorrectionRequest;
import com.rms.dto.response.IngredientResponse;
import com.rms.service.IngredientService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ingredients")
@RequiredArgsConstructor
public class IngredientController {

    private final IngredientService ingredientService;

    @GetMapping
    public ResponseEntity<List<IngredientResponse>> findAll() {
        return ResponseEntity.ok(ingredientService.findAll());
    }

    @GetMapping("/low-stock")
    public ResponseEntity<List<IngredientResponse>> findBelowReorderLevel() {
        return ResponseEntity.ok(ingredientService.findBelowReorderLevel());
    }

    @PostMapping
    public ResponseEntity<IngredientResponse> create(@Valid @RequestBody CreateIngredientRequest request) {
        Ingredient ingredient = Ingredient.builder()
                .name(request.name())
                .currentStock(request.currentStock())
                .reorderLevel(request.reorderLevel())
                .unitType(request.unitType())
                .build();
        return ResponseEntity.ok(ingredientService.create(ingredient, request.preferredSupplierId()));
    }

    /** Module 2.9 - physical stock-take correction. Goes through the same pessimistic path as a sale deduction. */
    @PatchMapping("/{id}/stock-correction")
    public ResponseEntity<IngredientResponse> correctStock(
            @PathVariable Long id, @Valid @RequestBody StockCorrectionRequest request
    ) {
        return ResponseEntity.ok(ingredientService.correctStock(id, request.newPhysicalCount()));
    }
}
