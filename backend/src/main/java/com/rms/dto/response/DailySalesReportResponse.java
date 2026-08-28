package com.rms.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailySalesReportResponse(
        LocalDate date,
        Integer totalOrders,
        BigDecimal totalRevenue,
        BigDecimal totalCogs,
        BigDecimal grossProfit,
        BigDecimal grossMarginPercent
) {}
