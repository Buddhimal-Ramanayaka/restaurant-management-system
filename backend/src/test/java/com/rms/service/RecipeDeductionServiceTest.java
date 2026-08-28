package com.rms.service;

import com.rms.domain.Ingredient;
import com.rms.domain.MenuItem;
import com.rms.domain.Recipe;
import com.rms.domain.enums.UnitType;
import com.rms.dto.response.MenuItemResponse;
import com.rms.event.ReorderThresholdBreachedEvent;
import com.rms.exception.InsufficientStockException;
import com.rms.repository.IngredientRepository;
import com.rms.repository.MenuItemRepository;
import com.rms.repository.RecipeRepository;
import com.rms.websocket.OrderEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the Recipe Deduction Engine (Module 2.2). Mirrors test cases
 * UT-01 through UT-07 from the dissertation Chapter 5 evaluation table.
 *
 * All repository/service collaborators are mocked - these tests verify the
 * ARITHMETIC and CONTROL FLOW of deductForOrder in isolation, not the actual
 * database locking behaviour (that correctness property is instead verified by
 * the concurrency integration test, IT-02, which needs a real database).
 */
@ExtendWith(MockitoExtension.class)
class RecipeDeductionServiceTest {

    @Mock private RecipeRepository recipeRepository;
    @Mock private IngredientRepository ingredientRepository;
    @Mock private MenuItemRepository menuItemRepository;
    @Mock private InventoryLedgerService inventoryLedgerService;
    @Mock private OrderEventPublisher publisher;
    @Mock private ApplicationEventPublisher eventPublisher;

    private RecipeDeductionService deductionService;

    private Ingredient rice;
    private Ingredient chicken;
    private MenuItem biryani;

    @BeforeEach
    void setUp() {
        deductionService = new RecipeDeductionService(
                recipeRepository, ingredientRepository, menuItemRepository,
                inventoryLedgerService, publisher, eventPublisher);

        rice = Ingredient.builder()
                .id(1L).name("Rice").currentStock(new BigDecimal("1000"))
                .reorderLevel(new BigDecimal("200")).unitType(UnitType.KG)
                .averageUnitCost(BigDecimal.TEN).build();

        chicken = Ingredient.builder()
                .id(2L).name("Chicken").currentStock(new BigDecimal("500"))
                .reorderLevel(new BigDecimal("100")).unitType(UnitType.KG)
                .averageUnitCost(BigDecimal.valueOf(20)).build();

        biryani = MenuItem.builder().id(10L).name("Chicken Biryani").isAvailable(true).build();
    }

    private Recipe recipeLine(MenuItem item, Ingredient ingredient, String qty) {
        return Recipe.builder().menuItem(item).ingredient(ingredient).quantityRequired(new BigDecimal(qty)).build();
    }

    @Test
    @DisplayName("UT-01: Single item deduction - sufficient stock")
    void singleItemDeduction_sufficientStock() {
        when(recipeRepository.findByMenuItemId(10L)).thenReturn(List.of(
                recipeLine(biryani, rice, "250"),
                recipeLine(biryani, chicken, "150")
        ));
        when(ingredientRepository.findAllByIdForUpdate(anyList())).thenReturn(List.of(rice, chicken));

        deductionService.deductForOrder(List.of(new RecipeDeductionService.DeductionLine(10L, 1)));

        assertThat(rice.getCurrentStock()).isEqualByComparingTo("750");
        assertThat(chicken.getCurrentStock()).isEqualByComparingTo("350");
        verify(inventoryLedgerService, times(2)).recordRecipeDeduction(any(), any(), any());
    }

    @Test
    @DisplayName("UT-02: Multi-item fold - shared ingredient deducted once, combined")
    void multiItemFold_sharedIngredient() {
        MenuItem friedRice = MenuItem.builder().id(11L).name("Fried Rice").isAvailable(true).build();

        when(recipeRepository.findByMenuItemId(10L)).thenReturn(List.of(recipeLine(biryani, rice, "250")));
        when(recipeRepository.findByMenuItemId(11L)).thenReturn(List.of(recipeLine(friedRice, rice, "150")));
        when(ingredientRepository.findAllByIdForUpdate(anyList())).thenReturn(List.of(rice));

        // 2x Biryani (500g rice) + 1x Fried Rice (150g rice) = 650g total, single ingredient row
        deductionService.deductForOrder(List.of(
                new RecipeDeductionService.DeductionLine(10L, 2),
                new RecipeDeductionService.DeductionLine(11L, 1)
        ));

        assertThat(rice.getCurrentStock()).isEqualByComparingTo("350"); // 1000 - 650
        // Exactly ONE ledger row for rice, not two - the fold collapsed both lines first.
        verify(inventoryLedgerService, times(1)).recordRecipeDeduction(eq(rice), any(), any());
    }

    @Test
    @DisplayName("UT-03: Insufficient stock - single ingredient rejects whole order")
    void insufficientStock_singleIngredient() {
        when(recipeRepository.findByMenuItemId(10L)).thenReturn(List.of(recipeLine(biryani, chicken, "600")));
        when(ingredientRepository.findAllByIdForUpdate(anyList())).thenReturn(List.of(chicken));

        assertThatThrownBy(() ->
                deductionService.deductForOrder(List.of(new RecipeDeductionService.DeductionLine(10L, 1)))
        ).isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("Chicken");

        assertThat(chicken.getCurrentStock()).isEqualByComparingTo("500"); // unchanged
        verify(ingredientRepository, never()).save(any());
    }

    @Test
    @DisplayName("UT-04: Partial shortfall still blocks the whole order (validate before mutate)")
    void insufficientStock_partialItemsOk() {
        MenuItem sideDish = MenuItem.builder().id(12L).name("Rice Side").isAvailable(true).build();

        // Rice: plenty of stock (would succeed alone). Chicken: not enough (would fail alone).
        when(recipeRepository.findByMenuItemId(10L)).thenReturn(List.of(recipeLine(biryani, chicken, "600")));
        when(recipeRepository.findByMenuItemId(12L)).thenReturn(List.of(recipeLine(sideDish, rice, "100")));
        when(ingredientRepository.findAllByIdForUpdate(anyList())).thenReturn(List.of(rice, chicken));

        assertThatThrownBy(() ->
                deductionService.deductForOrder(List.of(
                        new RecipeDeductionService.DeductionLine(10L, 1),
                        new RecipeDeductionService.DeductionLine(12L, 1)
                ))
        ).isInstanceOf(InsufficientStockException.class);

        // Neither ingredient was mutated - rice (which had enough) must NOT have been
        // decremented just because chicken failed validation.
        assertThat(rice.getCurrentStock()).isEqualByComparingTo("1000");
        assertThat(chicken.getCurrentStock()).isEqualByComparingTo("500");
        verify(ingredientRepository, never()).save(any());
    }

    @Test
    @DisplayName("UT-05: Zero stock auto-disables every menu item referencing that ingredient")
    void zeroStock_autoDisablesMenuItem() {
        chicken.setCurrentStock(new BigDecimal("150")); // exactly enough for one order, then hits zero
        Recipe biryaniChicken = recipeLine(biryani, chicken, "150");
        // In production this Set is populated by JPA's mappedBy fetch on MenuItem.recipes;
        // wire it here too so the auto-disable lookup (which walks menuItem.getRecipes())
        // has something to find against this hand-built mock entity.
        biryani.setRecipes(Set.of(biryaniChicken));
        when(recipeRepository.findByMenuItemId(10L)).thenReturn(List.of(biryaniChicken));
        when(ingredientRepository.findAllByIdForUpdate(anyList())).thenReturn(List.of(chicken));
        when(menuItemRepository.findAll()).thenReturn(List.of(biryani));

        deductionService.deductForOrder(List.of(new RecipeDeductionService.DeductionLine(10L, 1)));

        assertThat(chicken.getCurrentStock()).isEqualByComparingTo("0");
        assertThat(biryani.getIsAvailable()).isFalse();
        verify(menuItemRepository).save(biryani);

        // FR-09: every connected POS terminal must be told, not just left to refetch on reload.
        ArgumentCaptor<MenuItemResponse> captor = ArgumentCaptor.forClass(MenuItemResponse.class);
        verify(publisher).publishMenuItemAvailabilityChanged(captor.capture());
        assertThat(captor.getValue().id()).isEqualTo(biryani.getId());
        assertThat(captor.getValue().isAvailable()).isFalse();
    }

    @Test
    @DisplayName("UT-06: Reorder threshold publishes a breach event for async PO drafting (NFR-05)")
    void reorderThreshold_publishesBreachEvent() {
        // reorderLevel=100; deduct down to 80 -> breaches threshold but is not zero
        chicken.setCurrentStock(new BigDecimal("230"));
        when(recipeRepository.findByMenuItemId(10L)).thenReturn(List.of(recipeLine(biryani, chicken, "150")));
        when(ingredientRepository.findAllByIdForUpdate(anyList())).thenReturn(List.of(chicken));

        deductionService.deductForOrder(List.of(new RecipeDeductionService.DeductionLine(10L, 1)));

        assertThat(chicken.getCurrentStock()).isEqualByComparingTo("80");

        // The actual PO-drafting/dedup logic is no longer called synchronously from here at
        // all - it's covered in isolation by InventoryAlertServiceTest. This class's only
        // remaining responsibility on this path is to publish the event correctly.
        ArgumentCaptor<ReorderThresholdBreachedEvent> captor = ArgumentCaptor.forClass(ReorderThresholdBreachedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().ingredientId()).isEqualTo(chicken.getId());
        assertThat(captor.getValue().severity()).isEqualTo("LOW_STOCK");
    }

    @Test
    @DisplayName("Reorder breach at exactly zero stock is reported as OUT_OF_STOCK severity")
    void reorderThreshold_zeroStock_outOfStockSeverity() {
        chicken.setCurrentStock(new BigDecimal("150")); // exactly enough for one order, then hits zero
        when(recipeRepository.findByMenuItemId(10L)).thenReturn(List.of(recipeLine(biryani, chicken, "150")));
        when(ingredientRepository.findAllByIdForUpdate(anyList())).thenReturn(List.of(chicken));
        when(menuItemRepository.findAll()).thenReturn(List.of());

        deductionService.deductForOrder(List.of(new RecipeDeductionService.DeductionLine(10L, 1)));

        ArgumentCaptor<ReorderThresholdBreachedEvent> captor = ArgumentCaptor.forClass(ReorderThresholdBreachedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().severity()).isEqualTo("OUT_OF_STOCK");
    }
}
