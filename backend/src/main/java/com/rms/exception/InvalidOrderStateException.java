package com.rms.exception;

/** Thrown when a requested status transition is not legal from the order current state. */
public class InvalidOrderStateException extends RuntimeException {
    public InvalidOrderStateException(String message) {
        super(message);
    }
}
