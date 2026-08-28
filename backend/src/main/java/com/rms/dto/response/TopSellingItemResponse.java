package com.rms.dto.response;

import java.math.BigDecimal;

public record TopSellingItemResponse(
        String menuItemName,
        Integer quantitySold,
        BigDecimal revenue,
        BigDecimal cogs,
        BigDecimal marginPercent
) {}
