package com.rms.repository;

import com.rms.domain.WasteLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WasteLogRepository extends JpaRepository<WasteLog, Long> {
}
