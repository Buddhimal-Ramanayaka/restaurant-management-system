package com.rms.repository;

import com.rms.domain.RestaurantTable;
import com.rms.domain.enums.TableStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RestaurantTableRepository extends JpaRepository<RestaurantTable, Long> {

    List<RestaurantTable> findByOperationalStatus(TableStatus status);

    /**
     * Opening a POS session on a table is a check-then-act (is it AVAILABLE?) followed
     * by a write (flip to OCCUPIED). Locking the row here closes the same race window
     * as the ingredient lock above: two waiters tapping the same table within the same
     * instant must not both succeed in opening independent orders on it.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RestaurantTable t where t.id = :id")
    Optional<RestaurantTable> findByIdForUpdate(@Param("id") Long id);
}
