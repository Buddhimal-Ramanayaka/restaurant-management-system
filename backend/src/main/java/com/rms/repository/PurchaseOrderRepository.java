package com.rms.repository;

import com.rms.domain.PurchaseOrder;
import com.rms.domain.enums.PurchaseOrderStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {
    @EntityGraph(attributePaths = {"items", "items.ingredient", "supplier"})
    List<PurchaseOrder> findByStatus(PurchaseOrderStatus status);

    @EntityGraph(attributePaths = {"items", "items.ingredient", "supplier"})
    List<PurchaseOrder> findByStatusIn(List<PurchaseOrderStatus> statuses);

    boolean existsBySupplierIdAndStatusAndItems_Ingredient_Id(Long supplierId, PurchaseOrderStatus status, Long ingredientId);
}
