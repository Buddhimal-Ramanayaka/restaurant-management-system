package com.rms.service;

import com.rms.domain.RestaurantTable;
import com.rms.domain.enums.TableStatus;
import com.rms.exception.ResourceNotFoundException;
import com.rms.exception.TableUnavailableException;
import com.rms.repository.RestaurantTableRepository;
import com.rms.websocket.OrderEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the Table Status state machine (Module 2.6). Mirrors UT-08
 * through UT-10 from the dissertation Chapter 5 evaluation table.
 */
@ExtendWith(MockitoExtension.class)
class TableServiceTest {

    @Mock private RestaurantTableRepository tableRepository;
    @Mock private OrderEventPublisher publisher;

    private TableService tableService;
    private RestaurantTable table;

    @BeforeEach
    void setUp() {
        tableService = new TableService(tableRepository, publisher);
        table = RestaurantTable.builder()
                .id(5L).tableNumber("T-05").seatingCapacity(4)
                .operationalStatus(TableStatus.AVAILABLE)
                .build();
        // lenient: the rejection-path tests (UT-09, UT-10, not-found) never reach save()
        lenient().when(tableRepository.save(any(RestaurantTable.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("UT-08: Opening an AVAILABLE table transitions it to OCCUPIED")
    void openTable_availableState_transitionsToOccupied() {
        when(tableRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(table));

        RestaurantTable result = tableService.openTableForOrder(5L);

        assertThat(result.getOperationalStatus()).isEqualTo(TableStatus.OCCUPIED);
        verify(tableRepository).save(table);
    }

    @Test
    @DisplayName("UT-09: Opening an already-OCCUPIED table is rejected, no state change")
    void openTable_occupiedState_rejected() {
        table.setOperationalStatus(TableStatus.OCCUPIED);
        when(tableRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(table));

        assertThatThrownBy(() -> tableService.openTableForOrder(5L))
                .isInstanceOf(TableUnavailableException.class)
                .hasMessageContaining("OCCUPIED");

        assertThat(table.getOperationalStatus()).isEqualTo(TableStatus.OCCUPIED); // unchanged
        verify(tableRepository, never()).save(any());
    }

    @Test
    @DisplayName("UT-10: Opening a RESERVED table is rejected - walk-ins cannot steal a booking")
    void openTable_reservedState_rejected() {
        table.setOperationalStatus(TableStatus.RESERVED);
        when(tableRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(table));

        assertThatThrownBy(() -> tableService.openTableForOrder(5L))
                .isInstanceOf(TableUnavailableException.class)
                .hasMessageContaining("RESERVED");

        verify(tableRepository, never()).save(any());
    }

    @Test
    @DisplayName("Opening a non-existent table raises ResourceNotFoundException")
    void openTable_notFound() {
        when(tableRepository.findByIdForUpdate(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tableService.openTableForOrder(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("markCleaning clears the bound order id and broadcasts the new status")
    void markCleaning_clearsOrderIdAndBroadcasts() {
        table.setOperationalStatus(TableStatus.BILLED);
        table.setCurrentOrderId(42L);
        when(tableRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(table));

        RestaurantTable result = tableService.markCleaning(5L);

        assertThat(result.getOperationalStatus()).isEqualTo(TableStatus.CLEANING);
        assertThat(result.getCurrentOrderId()).isNull();
        verify(publisher).publishTableStatusChanged(any());
    }
}
