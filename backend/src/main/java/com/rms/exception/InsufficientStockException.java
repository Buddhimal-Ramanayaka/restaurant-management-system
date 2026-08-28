package com.rms.exception;

/** Thrown by RecipeDeductionService when a required ingredient does not have enough stock. */
public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(String message) {
        super(message);
    }
}
