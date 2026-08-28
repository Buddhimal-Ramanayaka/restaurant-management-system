package com.rms.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** Appendix B.2 - Kitchen Staff "Log Waste": ingredient, quantity, and reason code. */
public record RecordWasteRequest(
        @NotNull Long ingredientId,
        @NotNull @DecimalMin(value = "0.001", message = "quantityWasted must be positive") BigDecimal quantityWasted,
        @NotBlank String reasonCode // SPOILAGE, BREAKAGE, EXPIRY, CALIBRATION
) {}
