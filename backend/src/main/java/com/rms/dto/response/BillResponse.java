package com.rms.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record BillResponse(
        Long orderId,
        List<BillLineResponse> lines,
        BigDecimal subtotal,
        BigDecimal totalDiscount,
        BigDecimal serviceCharge,
        BigDecimal vat,
        BigDecimal total,
        String appliedPromotionName // null if none matched
) {}
