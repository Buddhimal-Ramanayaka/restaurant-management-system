package com.rms.dto.response;

import java.math.BigDecimal;

public record CustomerVisitSummaryResponse(
        String name,
        String phoneNumber,
        Integer visitCount,
        BigDecimal lifetimeSpend,
        String loyaltyTier
) {}
