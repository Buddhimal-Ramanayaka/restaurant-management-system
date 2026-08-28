package com.rms.repository;

import com.rms.domain.Order;
import com.rms.domain.enums.OrderStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @EntityGraph(attributePaths = {"items", "items.menuItem", "waiter"})
    List<Order> findByStatusInOrderByCreatedAtAsc(List<OrderStatus> statuses);

    @EntityGraph(attributePaths = {"items", "items.menuItem"})
    Order findWithItemsById(Long id);

    @Query("select o from Order o where o.waiter.id = :waiterId and o.status <> com.rms.domain.enums.OrderStatus.COMPLETED and o.status <> com.rms.domain.enums.OrderStatus.VOID")
    List<Order> findActiveOrdersForWaiter(Long waiterId);

    /**
     * Backs the daily sales report and top-selling-items analytics (Module 2.9/2.10).
     * Restricted to COMPLETED orders only - a BILLED-but-unpaid or VOID order must
     * never contribute to revenue or COGS figures.
     */
    @EntityGraph(attributePaths = {"items", "items.menuItem"})
    @Query("select o from Order o where o.status = com.rms.domain.enums.OrderStatus.COMPLETED "
            + "and o.createdAt >= :from and o.createdAt < :to")
    List<Order> findCompletedBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
