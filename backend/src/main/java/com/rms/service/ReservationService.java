package com.rms.service;

import com.rms.aspect.AuditableAction;
import com.rms.domain.Reservation;
import com.rms.domain.RestaurantTable;
import com.rms.domain.enums.TableStatus;
import com.rms.dto.request.CreateReservationRequest;
import com.rms.dto.response.ReservationResponse;
import com.rms.exception.ResourceNotFoundException;
import com.rms.exception.TableUnavailableException;
import com.rms.repository.ReservationRepository;
import com.rms.repository.RestaurantTableRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Module 2.11 - Reservation and Table Management. This is the class that makes
 * the RESERVED table state a real, reachable state rather than a schema column
 * nobody ever sets: creating a reservation flips the table to RESERVED, which
 * TableService.openTableForOrder then correctly refuses to open for a walk-in
 * (see IT-10). Checking a guest in on arrival releases the table back to
 * AVAILABLE so the waiter can open a normal POS session on it exactly as they
 * would for any walk-in.
 */
@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final RestaurantTableRepository tableRepository;
    private final TableService tableService;

    // DTO mapping happens INSIDE these @Transactional methods, not in the controller -
    // Reservation.table is lazy-fetched and this project deliberately runs with
    // open-in-view: false (see application.yml and the identical fix in
    // IngredientService/PurchaseOrderService/BillingService), so touching it after the
    // session closes throws LazyInitializationException. create() was already safe
    // (table came from a direct findById, not a lazy proxy); checkIn/cancel/findByTable
    // were not.

    @Transactional
    @AuditableAction("RESERVATION_CREATED")
    public ReservationResponse create(CreateReservationRequest request) {
        RestaurantTable table = tableRepository.findById(request.tableId())
                .orElseThrow(() -> new ResourceNotFoundException("Table not found: " + request.tableId()));

        if (table.getOperationalStatus() != TableStatus.AVAILABLE) {
            throw new TableUnavailableException(
                    "Table " + table.getTableNumber() + " is " + table.getOperationalStatus() + " and cannot be reserved right now");
        }

        Reservation reservation = Reservation.builder()
                .customerName(request.customerName())
                .customerPhone(request.customerPhone())
                .table(table)
                .reservationTime(request.reservationTime())
                .partySize(request.partySize())
                .status("BOOKED")
                .build();

        Reservation saved = reservationRepository.save(reservation);

        // Flips the table to RESERVED - this is the step that actually blocks
        // TableService.openTableForOrder from letting a walk-in take the table.
        tableService.setReserved(table.getId());

        return ReservationResponse.from(saved);
    }

    /** Guest has arrived: release the table back to AVAILABLE so a waiter can open it normally. */
    @Transactional
    @AuditableAction("RESERVATION_CHECKED_IN")
    public ReservationResponse checkIn(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found: " + reservationId));

        reservation.setStatus("CHECKED_IN");
        Reservation saved = reservationRepository.save(reservation);

        tableService.markAvailable(saved.getTable().getId());

        return ReservationResponse.from(saved);
    }

    @Transactional
    @AuditableAction("RESERVATION_CANCELLED")
    public ReservationResponse cancel(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found: " + reservationId));

        reservation.setStatus("CANCELLED");
        Reservation saved = reservationRepository.save(reservation);

        tableService.markAvailable(saved.getTable().getId());

        return ReservationResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> findByTable(Long tableId) {
        return reservationRepository.findByTableId(tableId).stream()
                .map(ReservationResponse::from).collect(Collectors.toList());
    }

    /** Reservations UI main list: every still-actionable booking, soonest first. */
    @Transactional(readOnly = true)
    public List<ReservationResponse> findUpcoming() {
        return reservationRepository.findByStatusOrderByReservationTimeAsc("BOOKED").stream()
                .map(ReservationResponse::from).collect(Collectors.toList());
    }
}
