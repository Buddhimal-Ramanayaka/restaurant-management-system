package com.rms.service;

import com.rms.domain.Ingredient;
import com.rms.domain.InventoryLedger;
import com.rms.domain.enums.LedgerReason;
import com.rms.repository.InventoryLedgerRepository;
import com.rms.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Thin, append-only writer for Module 2.7 / 2.9 audit trail. Every method here joins
 * the callers existing transaction (default REQUIRED) - a ledger row must never be
 * written unless the stock mutation it documents actually commits.
 */
@Service
@RequiredArgsConstructor
public class InventoryLedgerService {

    private final InventoryLedgerRepository ledgerRepository;
    private final UserRepository userRepository;

    @Transactional
    public void recordRecipeDeduction(Ingredient ingredient, BigDecimal signedDelta, BigDecimal resultingStock) {
        record(ingredient, signedDelta, resultingStock, LedgerReason.RECIPE_DEDUCTION, null, null);
    }

    @Transactional
    public void recordGrnReceipt(Ingredient ingredient, BigDecimal signedDelta, BigDecimal resultingStock, Long grnId) {
        record(ingredient, signedDelta, resultingStock, LedgerReason.GRN_RECEIPT, grnId, null);
    }

    @Transactional
    public void recordWaste(Ingredient ingredient, BigDecimal signedDelta, BigDecimal resultingStock, Long wasteLogId, Long recordedByUserId) {
        record(ingredient, signedDelta, resultingStock, LedgerReason.WASTE_SPOILAGE, wasteLogId, recordedByUserId);
    }

    @Transactional
    public void recordManualAdjustment(Ingredient ingredient, BigDecimal signedDelta, BigDecimal resultingStock, Long recordedByUserId) {
        record(ingredient, signedDelta, resultingStock, LedgerReason.MANUAL_ADJUSTMENT, null, recordedByUserId);
    }

    private void record(Ingredient ingredient, BigDecimal signedDelta, BigDecimal resultingStock,
                         LedgerReason reason, Long referenceId, Long recordedByUserId) {
        InventoryLedger entry = InventoryLedger.builder()
                .ingredient(ingredient)
                .quantityDelta(signedDelta)
                .resultingStock(resultingStock)
                .reason(reason)
                .referenceId(referenceId)
                .recordedBy(recordedByUserId != null ? userRepository.getReferenceById(recordedByUserId) : null)
                .build();
        ledgerRepository.save(entry);
    }
}
