package com.rms.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CloseShiftRequest(
        @NotNull @DecimalMin(value = "0", message = "declaredDrawerAmount cannot be negative") BigDecimal declaredDrawerAmount
) {}
