package com.rms.repository;

import com.rms.domain.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    List<Reservation> findByReservationTimeBetweenAndStatus(LocalDateTime from, LocalDateTime to, String status);
    List<Reservation> findByTableId(Long tableId);
    List<Reservation> findByStatusOrderByReservationTimeAsc(String status);
}
