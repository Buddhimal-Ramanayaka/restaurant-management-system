package com.rms.service;

import com.rms.aspect.AuditableAction;
import com.rms.domain.Ingredient;
import com.rms.dto.response.IngredientResponse;
import com.rms.exception.ResourceNotFoundException;
import com.rms.repository.IngredientRepository;
import com.rms.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IngredientService {

    private final IngredientRepository ingredientRepository;
    private final SupplierRepository supplierRepository;

    // DTO mapping happens INSIDE these @Transactional methods, not in the controller:
    // Ingredient.preferredSupplier is lazy-fetched and open-in-view is deliberately off
    // (see application.yml), so touching it after the session closes throws
    // LazyInitializationException - as findAll/findBelowReorderLevel/correctStock all did
    // here until this fix (never caught before because no ingredient had ever actually
    // dropped below its reorder level with the old placeholder seed data).

    @Transactional(readOnly = true)
    public List<IngredientResponse> findAll() {
        return ingredientRepository.findAll().stream().map(IngredientResponse::from).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<IngredientResponse> findBelowReorderLevel() {
        return ingredientRepository.findAllBelowReorderLevel().stream()
                .map(IngredientResponse::from).collect(Collectors.toList());
    }

    @Transactional
    public IngredientResponse create(Ingredient ingredient, Long preferredSupplierId) {
        if (preferredSupplierId != null) {
            ingredient.setPreferredSupplier(supplierRepository.findById(preferredSupplierId)
                    .orElseThrow(() -> new ResourceNotFoundException("Supplier not found: " + preferredSupplierId)));
        }
        return IngredientResponse.from(ingredientRepository.save(ingredient));
    }

    /**
     * Manual stock-take correction (Module 2.9: Physical vs Theoretical variance input).
     * Deliberately goes through the SAME pessimistic-write lookup as the deduction
     * engine, since a Manager correcting a count and a waiter submitting an order for
     * the same ingredient are exactly the kind of concurrent writers the lock exists for.
     */
    @Transactional
    @AuditableAction("STOCK_CORRECTION")
    public IngredientResponse correctStock(Long ingredientId, java.math.BigDecimal newPhysicalCount) {
        Ingredient ingredient = ingredientRepository.findByIdForUpdate(ingredientId)
                .orElseThrow(() -> new ResourceNotFoundException("Ingredient not found: " + ingredientId));
        ingredient.setCurrentStock(newPhysicalCount);
        return IngredientResponse.from(ingredientRepository.save(ingredient));
    }
}
