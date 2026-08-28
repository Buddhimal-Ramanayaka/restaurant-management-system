package com.rms.aspect;

import java.lang.annotation.*;

/**
 * Marks a service method whose invocation must be recorded in the audit_logs table. Applied
 * to high-privilege operations only (recipe edits, stock corrections, voids, PO approvals) -
 * routine reads and the hot-path order-submission flow are deliberately NOT annotated, since
 * logging every POS action would make the audit_logs table noise rather than signal.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditableAction {
    /** Short machine-readable action name, e.g. STOCK_CORRECTION, MENU_ITEM_UPDATE. */
    String value();
}
