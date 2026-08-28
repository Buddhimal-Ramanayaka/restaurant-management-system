package com.rms.dto.request;

/**
 * Appendix B (User Manual) - a PENDING order can be voided by the waiter directly;
 * an order already in PREPARING requires live Manager (or Admin) credential
 * verification, since the kitchen has already started consuming ingredients for it.
 * managerUsername/managerPassword are null for the PENDING case and required (checked
 * in OrderService.voidOrder) for the PREPARING case.
 */
public record VoidOrderRequest(
        String managerUsername,
        String managerPassword
) {}
