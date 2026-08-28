package com.rms.dto.response;

import com.rms.domain.WasteLog;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record WasteLogResponse(
        Long id,
        String ingredientName,
        BigDecimal quantityWasted,
        String reasonCode,
        String loggedByUsername,
        LocalDateTime loggedAt
) {
    public static WasteLogResponse from(WasteLog w) {
        return new WasteLogResponse(
                w.getId(), w.getIngredient().getName(), w.getQuantityWasted(),
                w.getReasonCode(), w.getLoggedBy().getUsername(), w.getLoggedAt());
    }
}
