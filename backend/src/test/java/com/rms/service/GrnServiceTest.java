package com.rms.service;

import com.rms.domain.GoodsReceivedNote;
import com.rms.domain.Ingredient;
import com.rms.domain.Supplier;
import com.rms.domain.enums.UnitType;
import com.rms.dto.request.GrnItemRequest;
import com.rms.dto.request.RecordGrnRequest;
import com.rms.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit test for GrnService's Weighted Average Cost recalculation. Mirrors UT-17
 * from the dissertation Chapter 5 evaluation table.
 */
@ExtendWith(MockitoExtension.class)
class GrnServiceTest {

    @Mock private GoodsReceivedNoteRepository grnRepository;
    @Mock private IngredientRepository ingredientRepository;
    @Mock private SupplierRepository supplierRepository;
    @Mock private PurchaseOrderRepository purchaseOrderRepository;
    @Mock private InventoryLedgerService inventoryLedgerService;
    @Mock private UserRepository userRepository;

    private GrnService grnService;
    private Ingredient rice;
    private Supplier supplier;

    @BeforeEach
    void setUp() {
        grnService = new GrnService(
                grnRepository, ingredientRepository, supplierRepository,
                purchaseOrderRepository, inventoryLedgerService, userRepository);

        rice = Ingredient.builder()
                .id(1L).name("Rice").currentStock(new BigDecimal("50"))
                .reorderLevel(new BigDecimal("10")).unitType(UnitType.KG)
                .averageUnitCost(new BigDecimal("400"))
                .build();

        supplier = Supplier.builder().id(1L).name("Lanka Agro").build();

        // Simulates IDENTITY generation: the first save (empty header) assigns an id;
        // the second save (after items are attached) is a no-op passthrough.
        when(grnRepository.save(any(GoodsReceivedNote.class))).thenAnswer(inv -> {
            GoodsReceivedNote grn = inv.getArgument(0);
            if (grn.getId() == null) {
                grn.setId(500L);
            }
            return grn;
        });
        when(ingredientRepository.save(any(Ingredient.class))).thenAnswer(inv -> inv.getArgument(0));
        when(supplierRepository.findById(1L)).thenReturn(Optional.of(supplier));
        when(ingredientRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(rice));
    }

    @Test
    @DisplayName("UT-17: WAC recalculation on GRN receipt - 50kg@400 existing + 20kg@500 received")
    void wacRecalculation_onGrnReceipt() {
        RecordGrnRequest request = new RecordGrnRequest(
                1L, LocalDate.now(), null,
                List.of(new GrnItemRequest(1L, new BigDecimal("20"), new BigDecimal("500")))
        );

        grnService.recordGrn(request, null);

        // New WAC = (50*400 + 20*500) / 70 = (20000 + 10000) / 70 = 428.5714 (scale 4, HALF_UP)
        assertThat(rice.getAverageUnitCost()).isEqualByComparingTo("428.5714");
        assertThat(rice.getCurrentStock()).isEqualByComparingTo("70"); // 50 + 20
        verify(inventoryLedgerService).recordGrnReceipt(eq(rice), eq(new BigDecimal("20")), eq(new BigDecimal("70")), eq(500L));
    }

    @Test
    @DisplayName("WAC falls back to the received unit cost outright when existing stock is zero")
    void wacRecalculation_zeroExistingStock_usesReceivedCostDirectly() {
        rice.setCurrentStock(BigDecimal.ZERO);
        rice.setAverageUnitCost(BigDecimal.ZERO);

        RecordGrnRequest request = new RecordGrnRequest(
                1L, LocalDate.now(), null,
                List.of(new GrnItemRequest(1L, new BigDecimal("30"), new BigDecimal("550")))
        );

        grnService.recordGrn(request, null);

        assertThat(rice.getAverageUnitCost()).isEqualByComparingTo("550");
        assertThat(rice.getCurrentStock()).isEqualByComparingTo("30");
    }
}
