package com.rms.controller;

import com.rms.dto.request.CreateReservationRequest;
import com.rms.dto.response.ReservationResponse;
import com.rms.service.ReservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @PostMapping
    public ResponseEntity<ReservationResponse> create(@Valid @RequestBody CreateReservationRequest request) {
        return ResponseEntity.ok(reservationService.create(request));
    }

    @PatchMapping("/{id}/check-in")
    public ResponseEntity<ReservationResponse> checkIn(@PathVariable Long id) {
        return ResponseEntity.ok(reservationService.checkIn(id));
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<ReservationResponse> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(reservationService.cancel(id));
    }

    @GetMapping
    public ResponseEntity<List<ReservationResponse>> findByTable(@RequestParam Long tableId) {
        return ResponseEntity.ok(reservationService.findByTable(tableId));
    }

    /** Reservations UI main list: every still-BOOKED reservation, soonest first. */
    @GetMapping("/upcoming")
    public ResponseEntity<List<ReservationResponse>> findUpcoming() {
        return ResponseEntity.ok(reservationService.findUpcoming());
    }
}
