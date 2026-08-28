package com.rms.dto.response;

import java.math.BigDecimal;

public record BillLineResponse(
        String menuItemName,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal lineDiscount,
        BigDecimal lineTotal
) {}
