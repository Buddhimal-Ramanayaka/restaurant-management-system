package com.rms.dto.response;

import com.rms.domain.Promotion;

import java.math.BigDecimal;

public record PromotionResponse(
        String name,
        BigDecimal discountPercent
) {
    public static PromotionResponse from(Promotion p) {
        return new PromotionResponse(p.getName(), p.getDiscountPercent());
    }
}
