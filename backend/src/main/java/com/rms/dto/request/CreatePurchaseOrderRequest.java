package com.rms.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** Manual PO creation by a Manager - distinct from the auto-drafted path in InventoryAlertService. */
public record CreatePurchaseOrderRequest(
        @NotNull Long supplierId,
        @NotEmpty @Valid List<PurchaseOrderItemRequest> items
) {}
