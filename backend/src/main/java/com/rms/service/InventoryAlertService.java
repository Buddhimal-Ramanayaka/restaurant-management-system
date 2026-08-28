package com.rms.service;

import com.rms.domain.Ingredient;
import com.rms.domain.PurchaseOrder;
import com.rms.domain.PurchaseOrderItem;
import com.rms.domain.Supplier;
import com.rms.domain.enums.PurchaseOrderStatus;
import com.rms.dto.response.StockAlertMessage;
import com.rms.event.ReorderThresholdBreachedEvent;
import com.rms.repository.IngredientRepository;
import com.rms.repository.PurchaseOrderRepository;
import com.rms.websocket.OrderEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;

/**
 * Module 2.2 (threshold alerts) + Module 2.12 (automated reorder-trigger -> draft PO).
 * Deliberately separate from RecipeDeductionService: the deduction engine job is to get
 * the arithmetic right and fail loudly on insufficient stock; this service job is to
 * react to the resulting stock level. Splitting them means a bug in "should we draft a
 * PO" can never roll back a stock deduction that already succeeded.
 *
 * NFR-05 requires PO drafting to be asynchronous, the same as stock-alert publishing - but
 * naively slapping @Async on a method still called mid-transaction from RecipeDeductionService
 * would race the deduction's own commit: the async thread could query the ingredient's stock
 * level before the deducting transaction has actually committed it, on a separate DB
 * connection with no lock relationship to the writer. @TransactionalEventListener(AFTER_COMMIT)
 * is what closes that gap - the event fires only once the publishing transaction has actually
 * committed, so the ingredient row this handler re-reads is guaranteed durable and correct.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryAlertService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final IngredientRepository ingredientRepository;
    private final OrderEventPublisher publisher;

    /**
     * Self-injected proxy, resolved lazily to sidestep the circular dependency this would
     * otherwise create. Calling handleReorderBreach via `this.` instead of `self.` compiles
     * fine but silently skips Spring's transactional proxy entirely - @Transactional(REQUIRES_NEW)
     * on that method would never actually run, and Ingredient.preferredSupplier (LAZY) would
     * blow up with LazyInitializationException the moment draftPurchaseOrderIfNeeded touches it
     * outside of any session, right after the PO itself had already saved via the repository's
     * own default transaction. Routing through `self` forces the call back through the proxy.
     */
    @Lazy
    @Autowired
    private InventoryAlertService self;

    /**
     * Thin, annotation-driven glue: runs on an @Async thread only after the transaction that
     * published the event has committed. Kept separate from handleReorderBreach so the actual
     * business logic stays a plain, directly unit-testable method.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReorderThresholdBreached(ReorderThresholdBreachedEvent event) {
        self.handleReorderBreach(event.ingredientId(), event.severity());
    }

    /**
     * Re-fetches the ingredient fresh (never trusts an entity reference from the triggering
     * transaction, which is long gone by the time this runs) inside its own new transaction,
     * drafts a PO if one isn't already in flight, and publishes the combined stock alert.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleReorderBreach(Long ingredientId, String severity) {
        Ingredient ingredient = ingredientRepository.findById(ingredientId).orElse(null);
        if (ingredient == null) {
            log.warn("Ingredient {} no longer exists by the time its reorder breach was processed - skipping", ingredientId);
            return;
        }

        Long draftPoId = draftPurchaseOrderIfNeeded(ingredient);

        publisher.publishStockAlert(new StockAlertMessage(
                ingredient.getId(), ingredient.getName(), ingredient.getCurrentStock(),
                ingredient.getReorderLevel(), severity, draftPoId
        ));
    }

    /**
     * Idempotency: if a DRAFT/PENDING_APPROVAL/ORDERED PO already exists for this ingredient +
     * supplier, we do not spawn a duplicate - we let the existing one ride, since it already
     * represents "more stock is coming".
     *
     * @return the id of the newly-drafted PO, or null if one was already in flight or the
     *         ingredient has no preferred supplier configured.
     */
    private Long draftPurchaseOrderIfNeeded(Ingredient ingredient) {
        Supplier supplier = ingredient.getPreferredSupplier();
        if (supplier == null) {
            log.warn("Ingredient {} breached reorder level but has no preferred supplier - skipping auto-PO", ingredient.getName());
            return null;
        }

        boolean alreadyInFlight = purchaseOrderRepository
                .existsBySupplierIdAndStatusAndItems_Ingredient_Id(supplier.getId(), PurchaseOrderStatus.DRAFT, ingredient.getId())
            || purchaseOrderRepository
                .existsBySupplierIdAndStatusAndItems_Ingredient_Id(supplier.getId(), PurchaseOrderStatus.PENDING_APPROVAL, ingredient.getId())
            || purchaseOrderRepository
                .existsBySupplierIdAndStatusAndItems_Ingredient_Id(supplier.getId(), PurchaseOrderStatus.ORDERED, ingredient.getId());

        if (alreadyInFlight) {
            return null;
        }

        // Reorder-to quantity: a simple, explainable heuristic - top back up to double the
        // reorder level. A real deployment would let Managers tune this per ingredient;
        // exposed here as a single constant multiplier to keep the structural intent clear.
        BigDecimal reorderToQuantity = ingredient.getReorderLevel().multiply(BigDecimal.valueOf(2))
                .subtract(ingredient.getCurrentStock())
                .max(ingredient.getReorderLevel());

        PurchaseOrderItem item = PurchaseOrderItem.builder()
                .ingredient(ingredient)
                .quantityOrdered(reorderToQuantity)
                .estimatedUnitCost(ingredient.getAverageUnitCost())
                .build();

        PurchaseOrder draft = PurchaseOrder.builder()
                .supplier(supplier)
                .status(PurchaseOrderStatus.DRAFT)
                .autoGenerated(true)
                .build();
        draft.getItems().add(item);
        item.setPurchaseOrder(draft);

        PurchaseOrder saved = purchaseOrderRepository.save(draft);
        log.info("Auto-drafted PO {} for ingredient {} ({} {}) from supplier {}",
                saved.getId(), ingredient.getName(), reorderToQuantity, ingredient.getUnitType(), supplier.getName());

        return saved.getId();
    }
}
