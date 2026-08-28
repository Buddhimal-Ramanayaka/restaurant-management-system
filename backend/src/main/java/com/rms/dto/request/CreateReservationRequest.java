package com.rms.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;

public record CreateReservationRequest(
        @NotBlank String customerName,
        @NotBlank String customerPhone,
        @NotNull Long tableId,
        @NotNull LocalDateTime reservationTime,
        @NotNull @Positive Integer partySize
) {}
