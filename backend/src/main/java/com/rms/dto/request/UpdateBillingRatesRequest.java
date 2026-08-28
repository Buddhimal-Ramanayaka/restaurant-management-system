package com.rms.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** FR-21 - rates are fractions (0.10 = 10%), matching how BillingService already applies them. */
public record UpdateBillingRatesRequest(
        @NotNull @DecimalMin(value = "0", message = "serviceChargeRate cannot be negative")
        @DecimalMax(value = "1", message = "serviceChargeRate cannot exceed 100%")
        BigDecimal serviceChargeRate,

        @NotNull @DecimalMin(value = "0", message = "vatRate cannot be negative")
        @DecimalMax(value = "1", message = "vatRate cannot exceed 100%")
        BigDecimal vatRate
) {}
