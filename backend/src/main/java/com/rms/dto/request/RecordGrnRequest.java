package com.rms.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record RecordGrnRequest(
        @NotNull Long supplierId,
        @NotNull LocalDate receivedDate,
        Long purchaseOrderId, // nullable - a delivery can arrive without a prior PO
        @NotEmpty @Valid List<GrnItemRequest> items
) {}
