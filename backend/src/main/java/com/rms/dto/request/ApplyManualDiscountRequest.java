package com.rms.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * FR-22 - a cashier-initiated discount requires live Manager (or Admin) credential
 * verification, not just their own session. managerUsername/Password are checked
 * against Spring Security's own AuthenticationManager - the same path a login uses -
 * without ever issuing a token for that manager.
 */
public record ApplyManualDiscountRequest(
        @NotBlank String managerUsername,
        @NotBlank String managerPassword,
        @NotNull @DecimalMin(value = "0.01", message = "discountPercent must be positive")
        @DecimalMax(value = "100", message = "discountPercent cannot exceed 100")
        java.math.BigDecimal discountPercent
) {}
