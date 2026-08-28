package com.rms.dto.request;

import jakarta.validation.constraints.NotBlank;

/** FR-14 / Appendix B.1 - "If not found, a new walk-in record can be registered." */
public record RegisterCustomerRequest(
        @NotBlank String name,
        @NotBlank String phoneNumber
) {}
