package com.rms.service;

import com.rms.aspect.AuditableAction;
import com.rms.domain.Supplier;
import com.rms.dto.request.SupplierRequest;
import com.rms.dto.response.SupplierResponse;
import com.rms.exception.ResourceNotFoundException;
import com.rms.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/** NFR-12: keeps SupplierController off the repository layer, consistent with every other module.
 *  create/update back the Admin "Manage Suppliers" use case (Figure 2.1). */
@Service
@RequiredArgsConstructor
public class SupplierService {

    private final SupplierRepository supplierRepository;

    @Transactional(readOnly = true)
    public List<SupplierResponse> findAll() {
        return supplierRepository.findAll().stream()
                .map(SupplierResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    @AuditableAction("SUPPLIER_CREATED")
    public SupplierResponse create(SupplierRequest request) {
        Supplier supplier = Supplier.builder()
                .name(request.name())
                .contactPhone(request.contactPhone())
                .contactEmail(request.contactEmail())
                .build();
        return SupplierResponse.from(supplierRepository.save(supplier));
    }

    @Transactional
    @AuditableAction("SUPPLIER_UPDATED")
    public SupplierResponse update(Long id, SupplierRequest request) {
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier not found: " + id));
        supplier.setName(request.name());
        supplier.setContactPhone(request.contactPhone());
        supplier.setContactEmail(request.contactEmail());
        return SupplierResponse.from(supplierRepository.save(supplier));
    }
}
