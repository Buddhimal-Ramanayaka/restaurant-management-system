package com.rms.dto.response;

import com.rms.domain.Reservation;

import java.time.LocalDateTime;

public record ReservationResponse(
        Long id,
        String customerName,
        String customerPhone,
        Long tableId,
        String tableNumber,
        LocalDateTime reservationTime,
        Integer partySize,
        String status
) {
    public static ReservationResponse from(Reservation r) {
        return new ReservationResponse(
                r.getId(), r.getCustomerName(), r.getCustomerPhone(),
                r.getTable().getId(), r.getTable().getTableNumber(),
                r.getReservationTime(), r.getPartySize(), r.getStatus()
        );
    }
}
