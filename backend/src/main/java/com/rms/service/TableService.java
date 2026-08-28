package com.rms.service;

import com.rms.domain.RestaurantTable;
import com.rms.domain.enums.TableStatus;
import com.rms.dto.response.TableResponse;
import com.rms.exception.ResourceNotFoundException;
import com.rms.exception.TableUnavailableException;
import com.rms.repository.RestaurantTableRepository;
import com.rms.websocket.OrderEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Module 2.6 - enforces the four (plus RESERVED) table states as an explicit state
 * machine rather than a free-form status column any controller can overwrite. Every
 * transition method here takes the pessimistic write lock from
 * RestaurantTableRepository#findByIdForUpdate first, closing the same
 * check-then-act race window the ingredient lock closes for stock.
 */
@Service
@RequiredArgsConstructor
public class TableService {

    private final RestaurantTableRepository tableRepository;
    private final OrderEventPublisher publisher;

    @Transactional(readOnly = true)
    public List<RestaurantTable> findAll() {
        return tableRepository.findAll();
    }

    /**
     * Called from OrderService inside the same transaction as order creation. A table
     * must be AVAILABLE (walk-in) to open a new POS session on it - RESERVED tables are
     * excluded on purpose, per Module 2.11: a reservation must be explicitly checked-in
     * first, so a walk-in cannot silently steal a booked table.
     */
    @Transactional
    public RestaurantTable openTableForOrder(Long tableId) {
        RestaurantTable table = tableRepository.findByIdForUpdate(tableId)
                .orElseThrow(() -> new ResourceNotFoundException("Table not found: " + tableId));

        if (table.getOperationalStatus() != TableStatus.AVAILABLE) {
            throw new TableUnavailableException(
                    "Table " + table.getTableNumber() + " is " + table.getOperationalStatus() + ", not available for a new order");
        }

        table.setOperationalStatus(TableStatus.OCCUPIED);
        return tableRepository.save(table);
    }

    @Transactional
    public RestaurantTable markBilled(Long tableId) {
        return transition(tableId, TableStatus.BILLED);
    }

    /** Guest departs after payment: table flips to CLEANING, not straight back to AVAILABLE. */
    @Transactional
    public RestaurantTable markCleaning(Long tableId) {
        RestaurantTable table = transition(tableId, TableStatus.CLEANING);
        table.setCurrentOrderId(null);
        return tableRepository.save(table);
    }

    @Transactional
    public RestaurantTable markAvailable(Long tableId) {
        return transition(tableId, TableStatus.AVAILABLE);
    }

    /** Used by OrderService#voidOrder - releases the table immediately without a CLEANING step. */
    @Transactional
    public RestaurantTable releaseTable(Long tableId) {
        RestaurantTable table = tableRepository.findByIdForUpdate(tableId)
                .orElseThrow(() -> new ResourceNotFoundException("Table not found: " + tableId));
        table.setOperationalStatus(TableStatus.AVAILABLE);
        table.setCurrentOrderId(null);
        RestaurantTable saved = tableRepository.save(table);
        publisher.publishTableStatusChanged(TableResponse.from(saved));
        return saved;
    }

    @Transactional
    public RestaurantTable setReserved(Long tableId) {
        return transition(tableId, TableStatus.RESERVED);
    }

    private RestaurantTable transition(Long tableId, TableStatus target) {
        RestaurantTable table = tableRepository.findByIdForUpdate(tableId)
                .orElseThrow(() -> new ResourceNotFoundException("Table not found: " + tableId));
        table.setOperationalStatus(target);
        RestaurantTable saved = tableRepository.save(table);
        publisher.publishTableStatusChanged(TableResponse.from(saved));
        return saved;
    }
}
