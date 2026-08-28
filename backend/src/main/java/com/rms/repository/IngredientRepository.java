package com.rms.repository;

import com.rms.domain.Ingredient;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface IngredientRepository extends JpaRepository<Ingredient, Long> {

    /**
     * THE concurrency-critical query in the whole system.
     *
     * Every write path that reads current_stock in order to mutate it - recipe
     * deduction, GRN receipt, waste logging, manual stock-take correction - MUST
     * go through this method instead of the inherited findById. PESSIMISTIC_WRITE
     * issues a SELECT ... FOR UPDATE, so a second waiter submitting an order for the
     * same ingredient blocks at the database until the first transaction commits or
     * rolls back, instead of both transactions reading stale stock and racing each
     * other to write a corrupted final value.
     *
     * The lock is only ever held for the lifetime of a single @Transactional method
     * (see RecipeDeductionService), so contention is measured in milliseconds even
     * under a busy pass, not seconds.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Ingredient i where i.id = :id")
    Optional<Ingredient> findByIdForUpdate(@Param("id") Long id);

    /**
     * Locks every ingredient row a single order touches in one round trip, in a
     * deterministic id-ascending order (see the ORDER BY). Locking in a fixed order
     * across all callers is what prevents deadlocks when two orders that share two or
     * more ingredients are submitted by two different waiters at the same instant -
     * without the ordering, transaction A could lock ingredient 7 then wait on 9 while
     * transaction B locks 9 then waits on 7.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Ingredient i where i.id in :ids order by i.id asc")
    List<Ingredient> findAllByIdForUpdate(@Param("ids") List<Long> ids);

    List<Ingredient> findByCurrentStockLessThanEqual(BigDecimal threshold);

    @Query("select i from Ingredient i where i.currentStock <= i.reorderLevel")
    List<Ingredient> findAllBelowReorderLevel();
}
