package com.rms.service;

import com.rms.aspect.AuditableAction;
import com.rms.domain.*;
import com.rms.domain.enums.PurchaseOrderStatus;
import com.rms.dto.request.GrnItemRequest;
import com.rms.dto.request.RecordGrnRequest;
import com.rms.dto.response.GrnResponse;
import com.rms.exception.ResourceNotFoundException;
import com.rms.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Module 2.7 - Goods Received Note processing. This is the procurement-side
 * counterpart to RecipeDeductionService: where that class is the only writer that
 * DECREASES ingredient stock from the sales path, this class is the only writer
 * that INCREASES it from the procurement path (Recipe/Waste/Manual-correction being
 * the other three ledger reasons, each owned by their own service).
 */
@Service
@RequiredArgsConstructor
public class GrnService {

    private final GoodsReceivedNoteRepository grnRepository;
    private final IngredientRepository ingredientRepository;
    private final SupplierRepository supplierRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final InventoryLedgerService inventoryLedgerService;
    private final UserRepository userRepository;

    /**
     * Locks each ingredient before mutating it - the same PESSIMISTIC_WRITE discipline
     * RecipeDeductionService uses - because a GRN receipt and a concurrent sale deduction
     * on the same ingredient are exactly the kind of concurrent writers pessimistic
     * locking exists to serialise correctly.
     */
    // DTO mapping happens INSIDE these @Transactional methods, not in the controller -
    // GoodsReceivedNote.supplier/purchaseOrder and GrnItem.ingredient are lazy-fetched
    // and this project deliberately runs with open-in-view: false (see application.yml
    // and the identical fix already applied in Ingredient/PurchaseOrder/Billing/
    // ReservationService). recordGrn's own return value happened to be safe already
    // (every association on it was set from an already-loaded object, never a lazy
    // proxy fetched fresh) but is converted here too for consistency; findAll() was not
    // safe - a plain findAll() with no @EntityGraph leaves every association lazy.

    @Transactional
    @AuditableAction("GRN_RECEIVED")
    public GrnResponse recordGrn(RecordGrnRequest request, Long recordedByUserId) {
        Supplier supplier = supplierRepository.findById(request.supplierId())
                .orElseThrow(() -> new ResourceNotFoundException("Supplier not found: " + request.supplierId()));

        User recordedBy = recordedByUserId != null
                ? userRepository.findById(recordedByUserId).orElse(null)
                : null;

        PurchaseOrder purchaseOrder = null;
        if (request.purchaseOrderId() != null) {
            purchaseOrder = purchaseOrderRepository.findById(request.purchaseOrderId())
                    .orElseThrow(() -> new ResourceNotFoundException("Purchase order not found: " + request.purchaseOrderId()));
        }

        GoodsReceivedNote grn = GoodsReceivedNote.builder()
                .supplier(supplier)
                .receivedDate(request.receivedDate())
                .recordedBy(recordedBy)
                .purchaseOrder(purchaseOrder)
                .build();

        // Persist the header FIRST (IDENTITY generation assigns grn.getId() synchronously
        // on this insert) so the ledger rows written inside the loop below can carry a
        // real, non-null referenceId back to this GRN - getting this ordering wrong would
        // silently write every ledger entry with referenceId=null, breaking the audit trail
        // this whole module exists to provide.
        grn = grnRepository.save(grn);

        for (GrnItemRequest itemRequest : request.items()) {
            // Pessimistic lock BEFORE reading current_stock/average_unit_cost, for the
            // same reason RecipeDeductionService locks before reading: a stale read here
            // would produce a WAC computed against a stock level that a concurrent sale
            // deduction has already moved on from.
            Ingredient ingredient = ingredientRepository.findByIdForUpdate(itemRequest.ingredientId())
                    .orElseThrow(() -> new ResourceNotFoundException("Ingredient not found: " + itemRequest.ingredientId()));

            BigDecimal newWac = recalculateWeightedAverageCost(
                    ingredient.getCurrentStock(), ingredient.getAverageUnitCost(),
                    itemRequest.quantityReceived(), itemRequest.unitCost());

            BigDecimal newStock = ingredient.getCurrentStock().add(itemRequest.quantityReceived());

            ingredient.setCurrentStock(newStock);
            ingredient.setAverageUnitCost(newWac);
            ingredientRepository.save(ingredient);

            inventoryLedgerService.recordGrnReceipt(ingredient, itemRequest.quantityReceived(), newStock, grn.getId());

            grn.getItems().add(GrnItem.builder()
                    .grn(grn)
                    .ingredient(ingredient)
                    .quantityReceived(itemRequest.quantityReceived())
                    .unitCost(itemRequest.unitCost())
                    .build());
        }

        GoodsReceivedNote saved = grnRepository.save(grn);

        if (purchaseOrder != null) {
            purchaseOrder.setStatus(PurchaseOrderStatus.RECEIVED);
            purchaseOrderRepository.save(purchaseOrder);
        }

        return GrnResponse.from(saved);
    }

    /**
     * FR-28: New WAC = (Existing Stock * Old Cost + Received Qty * New Unit Cost)
     *        / (Existing Stock + Received Qty)
     *
     * Falls back to the received unit cost outright when existing stock is zero
     * (there is nothing to weight-average against yet - the first delivery of a
     * brand-new ingredient sets its opening cost).
     */
    private BigDecimal recalculateWeightedAverageCost(
            BigDecimal existingStock, BigDecimal oldCost, BigDecimal receivedQty, BigDecimal newUnitCost) {

        BigDecimal totalQty = existingStock.add(receivedQty);
        if (totalQty.compareTo(BigDecimal.ZERO) == 0) {
            return newUnitCost;
        }

        BigDecimal existingValue = existingStock.multiply(oldCost);
        BigDecimal receivedValue = receivedQty.multiply(newUnitCost);

        return existingValue.add(receivedValue).divide(totalQty, 4, RoundingMode.HALF_UP);
    }

    @Transactional(readOnly = true)
    public List<GrnResponse> findAll() {
        return grnRepository.findAll().stream().map(GrnResponse::from).collect(Collectors.toList());
    }
}
