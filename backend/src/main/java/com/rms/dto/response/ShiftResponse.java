package com.rms.dto.response;

import com.rms.domain.Shift;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ShiftResponse(
        Long id,
        Long cashierId,
        String cashierUsername,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        BigDecimal systemCashTotal,
        BigDecimal systemCardTotal,
        BigDecimal systemDigitalTotal,
        BigDecimal declaredDrawerAmount,
        BigDecimal variance,
        String reviewedByUsername
) {
    public static ShiftResponse from(Shift s) {
        return new ShiftResponse(
                s.getId(), s.getCashier().getId(), s.getCashier().getUsername(),
                s.getStartedAt(), s.getEndedAt(),
                s.getSystemCashTotal(), s.getSystemCardTotal(), s.getSystemDigitalTotal(),
                s.getDeclaredDrawerAmount(), s.getVariance(),
                s.getReviewedBy() != null ? s.getReviewedBy().getUsername() : null
        );
    }
}
