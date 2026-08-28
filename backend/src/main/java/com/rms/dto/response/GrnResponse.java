package com.rms.dto.response;

import com.rms.domain.GoodsReceivedNote;

import java.time.LocalDate;
import java.util.List;

public record GrnResponse(
        Long id,
        String supplierName,
        LocalDate receivedDate,
        Long purchaseOrderId,
        List<String> ingredientSummaries
) {
    public static GrnResponse from(GoodsReceivedNote grn) {
        List<String> summaries = grn.getItems().stream()
                .map(item -> item.getIngredient().getName() + ": +" + item.getQuantityReceived()
                        + " @ " + item.getUnitCost())
                .toList();
        return new GrnResponse(
                grn.getId(), grn.getSupplier().getName(), grn.getReceivedDate(),
                grn.getPurchaseOrder() != null ? grn.getPurchaseOrder().getId() : null,
                summaries
        );
    }
}
