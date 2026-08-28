package com.rms.domain.enums;

/** Module 2.6 state machine. Transitions are enforced in TableService, not by JPA. */
public enum TableStatus {
    AVAILABLE,
    OCCUPIED,
    BILLED,
    CLEANING,
    RESERVED
}
