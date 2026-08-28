package com.rms.dto.response;

import com.rms.domain.Supplier;

public record SupplierResponse(Long id, String name, String contactPhone, String contactEmail) {
    public static SupplierResponse from(Supplier s) {
        return new SupplierResponse(s.getId(), s.getName(), s.getContactPhone(), s.getContactEmail());
    }
}
