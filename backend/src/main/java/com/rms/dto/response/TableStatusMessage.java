package com.rms.dto.response;

/** Payload broadcast on /topic/tables whenever the floor-plan state machine transitions. */
public record TableStatusMessage(
        TableResponse table
) {}
