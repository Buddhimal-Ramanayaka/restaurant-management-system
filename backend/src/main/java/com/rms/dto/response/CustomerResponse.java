package com.rms.dto.response;

import com.rms.domain.Customer;

import java.math.BigDecimal;

public record CustomerResponse(
        Long id,
        String name,
        String phoneNumber,
        Integer visitCount,
        BigDecimal lifetimeSpend,
        String loyaltyTier
) {
    public static CustomerResponse from(Customer c) {
        return new CustomerResponse(
                c.getId(), c.getName(), c.getPhoneNumber(),
                c.getVisitCount(), c.getLifetimeSpend(), c.getLoyaltyTier()
        );
    }
}
