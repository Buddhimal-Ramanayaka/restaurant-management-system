package com.rms.service;

import com.rms.domain.Ingredient;
import com.rms.domain.MenuItem;
import com.rms.domain.Order;
import com.rms.domain.Recipe;
import com.rms.dto.response.MenuItemResponse;
import com.rms.event.ReorderThresholdBreachedEvent;
import com.rms.exception.InsufficientStockException;
import com.rms.repository.IngredientRepository;
import com.rms.repository.MenuItemRepository;
import com.rms.repository.RecipeRepository;
import com.rms.websocket.OrderEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * Module 2.2 - The Core System Engine.
 *
 * Responsibility: given the line items of an order that is about to be submitted to the
 * kitchen, walk every Menu_Item back to its Recipe rows and subtract
 *   New Stock = Current Stock - (Ordered Quantity * Required Recipe Quantity)
 * from every raw ingredient that recipe touches, atomically, and raise the two
 * downstream side effects the spec calls for: auto-disabling a menu item that hits
 * zero stock, and flagging (auto-drafting a PO for) anything under its reorder level.
 *
 * This class is intentionally the ONLY place in the codebase that mutates
 * Ingredient.currentStock outside of GRN receipt / waste logging / manual correction -
 * concentrating the arithmetic here is what makes the inventory ledger trustworthy.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecipeDeductionService {

    private final RecipeRepository recipeRepository;
    private final IngredientRepository ingredientRepository;
    private final MenuItemRepository menuItemRepository;
    private final InventoryLedgerService inventoryLedgerService;
    private final OrderEventPublisher publisher;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * A single order line: "3 x Chicken Biryani". Kept internal to this class so
     * OrderService can pass in plain (menuItemId, quantity) pairs without a dependency
     * on the JPA OrderDetail entity - this service should be callable in isolation
     * (e.g. from a unit test) without spinning up an Order aggregate first.
     */
    public record DeductionLine(Long menuItemId, Integer quantity) {}

    /**
     * Runs the whole deduction for one order inside the SAME transaction as the order
     * insert itself (REQUIRED, the default, is used deliberately - see OrderService,
     * which opens the outer @Transactional boundary). If any ingredient comes up short,
     * this method throws, the whole transaction rolls back, and the order is never
     * persisted - the waiter sees a clear "cannot submit" error instead of a partially
     * decremented pantry.
     *
     * Step-by-step:
     *   1. Resolve every (menuItem -> recipe lines) up front, and fold them into a
     *      single required-quantity-per-ingredient map. This is what lets a ticket that
     *      orders two different dishes sharing an ingredient (e.g. both use garlic) be
     *      checked and locked ONCE per ingredient, not once per dish.
     *   2. Lock every ingredient row touched, in one query, in ascending id order
     *      (see IngredientRepository#findAllByIdForUpdate) - this is what prevents the
     *      classic two-waiters-two-shared-ingredients deadlock.
     *   3. Validate first, mutate second: check every ingredient has enough stock
     *      BEFORE writing any of them, so a shortfall on ingredient #9 does not leave
     *      ingredient #3 already decremented.
     *   4. Apply the subtraction, append one inventory_ledger row per ingredient, and
     *      run the threshold checks (auto-disable menu item at zero, low-stock alert,
     *      auto-draft PO) on the post-write state.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void deductForOrder(List<DeductionLine> lines) {
        if (lines.isEmpty()) {
            return;
        }

        // --- Step 1: fold every line into total required quantity per ingredient ---
        Map<Long, BigDecimal> requiredByIngredientId = new HashMap<>();
        Map<Long, MenuItem> menuItemTouchingIngredient = new HashMap<>(); // for the auto-disable step

        for (DeductionLine line : lines) {
            List<Recipe> recipeLines = recipeRepository.findByMenuItemId(line.menuItemId());

            for (Recipe recipe : recipeLines) {
                Long ingredientId = recipe.getIngredient().getId();
                BigDecimal consumed = recipe.getQuantityRequired()
                        .multiply(BigDecimal.valueOf(line.quantity()));

                requiredByIngredientId.merge(ingredientId, consumed, BigDecimal::add);
                menuItemTouchingIngredient.putIfAbsent(ingredientId, recipe.getMenuItem());
            }
        }

        if (requiredByIngredientId.isEmpty()) {
            log.warn("Order line set produced no recipe mappings - no ingredients to deduct");
            return;
        }

        // --- Step 2: lock every touched ingredient row, deterministically ordered ---
        List<Long> ingredientIds = new ArrayList<>(requiredByIngredientId.keySet());
        Collections.sort(ingredientIds);
        List<Ingredient> lockedIngredients = ingredientRepository.findAllByIdForUpdate(ingredientIds);

        Map<Long, Ingredient> ingredientsById = new HashMap<>();
        for (Ingredient ingredient : lockedIngredients) {
            ingredientsById.put(ingredient.getId(), ingredient);
        }

        // --- Step 3: validate BEFORE mutating anything ---
        List<String> shortfalls = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> entry : requiredByIngredientId.entrySet()) {
            Ingredient ingredient = ingredientsById.get(entry.getKey());
            if (ingredient == null) {
                shortfalls.add("Ingredient id " + entry.getKey() + " no longer exists");
                continue;
            }
            if (ingredient.getCurrentStock().compareTo(entry.getValue()) < 0) {
                shortfalls.add(String.format(
                        "%s: need %s %s but only %s %s in stock",
                        ingredient.getName(), entry.getValue(), ingredient.getUnitType(),
                        ingredient.getCurrentStock(), ingredient.getUnitType()));
            }
        }

        if (!shortfalls.isEmpty()) {
            throw new InsufficientStockException(
                    "Cannot submit order - insufficient stock: " + String.join("; ", shortfalls));
        }

        // --- Step 4: apply subtraction, ledger entry, and threshold side effects ---
        for (Map.Entry<Long, BigDecimal> entry : requiredByIngredientId.entrySet()) {
            Ingredient ingredient = ingredientsById.get(entry.getKey());
            BigDecimal requiredQty = entry.getValue();

            BigDecimal newStock = ingredient.getCurrentStock().subtract(requiredQty);
            ingredient.setCurrentStock(newStock);
            ingredientRepository.save(ingredient);

            inventoryLedgerService.recordRecipeDeduction(ingredient, requiredQty.negate(), newStock);

            handlePostDeductionThresholds(ingredient, menuItemTouchingIngredient.get(ingredient.getId()));
        }
    }

    /**
     * Two independent threshold checks, both driven off the freshly-written stock level:
     *   - stock == 0  -> toggle every menu item that recipes off this ingredient to
     *                    unavailable, so the POS grid stops offering something the
     *                    kitchen physically cannot make.
     *   - stock <= reorder_level -> push a real-time alert to Admin/Manager and, per
     *                    Module 2.12, hand off to InventoryAlertService to auto-draft a
     *                    PO against the ingredient preferred supplier if one does not
     *                    already exist in flight.
     */
    private void handlePostDeductionThresholds(Ingredient ingredient, MenuItem representativeMenuItem) {
        if (ingredient.getCurrentStock().compareTo(BigDecimal.ZERO) <= 0) {
            List<MenuItem> affected = menuItemRepository.findAll().stream()
                    .filter(mi -> mi.getRecipes().stream()
                            .anyMatch(r -> r.getIngredient().getId().equals(ingredient.getId())))
                    .toList();
            for (MenuItem menuItem : affected) {
                if (Boolean.TRUE.equals(menuItem.getIsAvailable())) {
                    menuItem.setIsAvailable(false);
                    menuItemRepository.save(menuItem);
                    log.info("Auto-disabled menu item {} - ingredient {} hit zero stock",
                            menuItem.getName(), ingredient.getName());
                    // FR-09: every connected POS terminal must reflect this without a page reload.
                    publisher.publishMenuItemAvailabilityChanged(MenuItemResponse.from(menuItem));
                }
            }
        }

        if (ingredient.getCurrentStock().compareTo(ingredient.getReorderLevel()) <= 0) {
            String severity = ingredient.getCurrentStock().compareTo(BigDecimal.ZERO) <= 0
                    ? "OUT_OF_STOCK" : "LOW_STOCK";

            // NFR-05: PO drafting must be asynchronous. Publishing this event (rather than
            // calling InventoryAlertService directly) is what makes that safe - see
            // InventoryAlertService.onReorderThresholdBreached for why AFTER_COMMIT matters here.
            eventPublisher.publishEvent(new ReorderThresholdBreachedEvent(ingredient.getId(), severity));
        }
    }
}
