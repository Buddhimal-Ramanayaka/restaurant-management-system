package com.rms.service;

import com.rms.aspect.AuditableAction;
import com.rms.domain.Ingredient;
import com.rms.domain.User;
import com.rms.domain.WasteLog;
import com.rms.dto.request.RecordWasteRequest;
import com.rms.dto.response.WasteLogResponse;
import com.rms.event.ReorderThresholdBreachedEvent;
import com.rms.exception.InsufficientStockException;
import com.rms.exception.ResourceNotFoundException;
import com.rms.repository.IngredientRepository;
import com.rms.repository.UserRepository;
import com.rms.repository.WasteLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Module 2.9 - non-sales stock reductions (Appendix B.2: "Logging Waste" from the
 * Kitchen Display). The ledger-writing half of this (InventoryLedgerService.recordWaste)
 * already existed, built ahead of this service in anticipation of it; this class is the
 * missing piece that actually calls it - previously waste_logs was schema-only, with no
 * service, controller, or UI touching it at all.
 *
 * Pessimistic-write locking before mutating stock, same discipline as
 * RecipeDeductionService and GrnService: a waste log and a concurrent sale deduction on
 * the same ingredient are exactly the kind of concurrent writers that need serialising.
 */
@Service
@RequiredArgsConstructor
public class WasteLogService {

    private final WasteLogRepository wasteLogRepository;
    private final IngredientRepository ingredientRepository;
    private final UserRepository userRepository;
    private final InventoryLedgerService inventoryLedgerService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    @AuditableAction("WASTE_LOGGED")
    public WasteLogResponse record(RecordWasteRequest request, Long loggedByUserId) {
        Ingredient ingredient = ingredientRepository.findByIdForUpdate(request.ingredientId())
                .orElseThrow(() -> new ResourceNotFoundException("Ingredient not found: " + request.ingredientId()));

        if (ingredient.getCurrentStock().compareTo(request.quantityWasted()) < 0) {
            throw new InsufficientStockException(
                    "Cannot log " + request.quantityWasted() + " " + ingredient.getUnitType()
                            + " wasted - only " + ingredient.getCurrentStock() + " " + ingredient.getUnitType() + " in stock");
        }

        User loggedBy = userRepository.findById(loggedByUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + loggedByUserId));

        BigDecimal newStock = ingredient.getCurrentStock().subtract(request.quantityWasted());
        ingredient.setCurrentStock(newStock);
        ingredientRepository.save(ingredient);

        WasteLog wasteLog = WasteLog.builder()
                .ingredient(ingredient)
                .quantityWasted(request.quantityWasted())
                .reasonCode(request.reasonCode())
                .loggedBy(loggedBy)
                .build();
        WasteLog saved = wasteLogRepository.save(wasteLog);

        inventoryLedgerService.recordWaste(ingredient, request.quantityWasted().negate(), newStock, saved.getId(), loggedByUserId);

        // Waste is just another way stock depletes - the reorder-threshold alert and
        // auto-drafted PO should fire exactly as they would from a sale, via the same
        // event InventoryAlertService listens for from RecipeDeductionService (NFR-05:
        // published here, handled asynchronously after this transaction commits).
        // Deliberately NOT reusing the zero-stock "auto-disable menu items" behaviour
        // here: that logic is private to RecipeDeductionService and duplicating/extracting
        // it is a bigger, separate change than this feature calls for.
        if (newStock.compareTo(ingredient.getReorderLevel()) <= 0) {
            String severity = newStock.compareTo(BigDecimal.ZERO) <= 0 ? "OUT_OF_STOCK" : "LOW_STOCK";
            eventPublisher.publishEvent(new ReorderThresholdBreachedEvent(ingredient.getId(), severity));
        }

        return WasteLogResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<WasteLogResponse> findAll() {
        return wasteLogRepository.findAll().stream().map(WasteLogResponse::from).collect(Collectors.toList());
    }
}
