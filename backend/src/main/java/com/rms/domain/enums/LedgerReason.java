package com.rms.domain.enums;

/** Every row in inventory_ledger carries one of these so variance reports can group by cause. */
public enum LedgerReason {
    RECIPE_DEDUCTION,
    GRN_RECEIPT,
    MANUAL_ADJUSTMENT,
    WASTE_SPOILAGE,
    STOCK_TAKE_CORRECTION
}
