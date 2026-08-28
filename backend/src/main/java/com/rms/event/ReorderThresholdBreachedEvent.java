package com.rms.event;

/**
 * Published (never handled) by RecipeDeductionService / WasteLogService the instant an
 * ingredient's stock crosses its reorder level. Deliberately carries only an id, not the
 * Ingredient entity itself - the entity is bound to the publishing thread's persistence
 * context, which is gone by the time the @Async @TransactionalEventListener handling this
 * event runs on a different thread after the publishing transaction commits (see
 * InventoryAlertService.onReorderThresholdBreached).
 */
public record ReorderThresholdBreachedEvent(Long ingredientId, String severity) {}
