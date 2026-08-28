package com.rms.domain.enums;

/**
 * Mirrors the orders.status ENUM from the schema spec.
 *
 * Flow: PENDING -(kitchen accepts)-> PREPARING -(kitchen finishes)-> READY
 *       -(waiter delivers, cashier bills)-> BILLED -(payment settled)-> COMPLETED
 * VOID is a terminal side-branch reachable from PENDING/PREPARING only (see OrderService).
 *
 * Stock deduction (RecipeDeductionService) fires at order-creation time, i.e. the moment
 * the waiter submits the ticket to the kitchen and the order is persisted as PENDING.
 * That submission instant is the concrete mapping of the SUBMITTED / CONFIRMED trigger
 * described in Module 2.2 of the spec onto this concrete enum PENDING state.
 */
public enum OrderStatus {
    PENDING,
    PREPARING,
    READY,
    BILLED,
    COMPLETED,
    VOID
}
