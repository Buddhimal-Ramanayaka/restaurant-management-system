package com.rms.dto.request;

import jakarta.validation.constraints.NotBlank;

/** Admin "Manage Suppliers" use case (Figure 2.1) - shared shape for create and update. */
public record SupplierRequest(
        @NotBlank String name,
        String contactPhone,
        String contactEmail
) {}
