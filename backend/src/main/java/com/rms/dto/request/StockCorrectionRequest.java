package com.rms.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** Module 2.9 - manager records the result of a physical cycle count. */
public record StockCorrectionRequest(
        @NotNull @DecimalMin(value = "0", message = "physical count cannot be negative") BigDecimal newPhysicalCount
) {}
