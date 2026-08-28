package com.rms.repository;

import com.rms.domain.Shift;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ShiftRepository extends JpaRepository<Shift, Long> {
    Optional<Shift> findTopByCashierIdAndEndedAtIsNullOrderByStartedAtDesc(Long cashierId);

    /** Manager Shift Review list (Module 2.8/Appendix C) - closed shifts, most recently ended first. */
    List<Shift> findByEndedAtIsNotNullOrderByEndedAtDesc();
}
