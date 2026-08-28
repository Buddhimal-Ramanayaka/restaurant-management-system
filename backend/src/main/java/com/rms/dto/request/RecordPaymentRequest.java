package com.rms.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record RecordPaymentRequest(
        @NotNull Long shiftId,
        @NotNull String paymentMethod, // CASH, CARD, DIGITAL
        @NotNull @DecimalMin(value = "0.01", message = "amount must be positive") BigDecimal amount,
        // Optional - carries forward a discount already manager-authorized via
        // POST /api/billing/orders/{id}/manual-discount, recomputed identically at
        // settlement time so the receipt matches what the cashier previewed.
        BigDecimal manualDiscountPercent
) {}
