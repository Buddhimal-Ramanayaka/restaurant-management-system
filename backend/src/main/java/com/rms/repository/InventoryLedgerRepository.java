package com.rms.repository;

import com.rms.domain.InventoryLedger;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InventoryLedgerRepository extends JpaRepository<InventoryLedger, Long> {
    List<InventoryLedger> findByIngredientIdOrderByRecordedAtDesc(Long ingredientId);
}
