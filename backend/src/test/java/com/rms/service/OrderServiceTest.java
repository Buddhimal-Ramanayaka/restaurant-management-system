package com.rms.service;

import com.rms.domain.Order;
import com.rms.domain.OrderDetail;
import com.rms.domain.RestaurantTable;
import com.rms.domain.User;
import com.rms.domain.enums.OrderStatus;
import com.rms.domain.enums.Role;
import com.rms.exception.InvalidOrderStateException;
import com.rms.exception.ResourceNotFoundException;
import com.rms.repository.*;
import com.rms.websocket.OrderEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the OrderService lifecycle orchestration. Mirrors UT-11 through
 * UT-14 from the dissertation Chapter 5 evaluation table. RecipeDeductionService
 * and TableService are mocked here - their own correctness is verified by
 * RecipeDeductionServiceTest and TableServiceTest respectively; these tests only
 * verify OrderService's own state-machine and orchestration logic.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderDetailRepository orderDetailRepository;
    @Mock private MenuItemRepository menuItemRepository;
    @Mock private UserRepository userRepository;
    @Mock private RestaurantTableRepository tableRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private RecipeDeductionService recipeDeductionService;
    @Mock private TableService tableService;
    @Mock private OrderEventPublisher publisher;
    @Mock private ManagerAuthorizationService managerAuthorizationService;

    private OrderService orderService;

    private Order order;
    private RestaurantTable table;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(
                orderRepository, orderDetailRepository, menuItemRepository, userRepository,
                tableRepository, customerRepository, recipeDeductionService, tableService, publisher,
                managerAuthorizationService);

        User waiter = User.builder().id(1L).username("kamal").role(Role.WAITER).build();

        OrderDetail line = OrderDetail.builder().id(100L).quantity(1).lineStatus(OrderStatus.PENDING).build();

        order = Order.builder()
                .id(500L).tableId(5L).waiter(waiter).status(OrderStatus.PENDING)
                .items(new java.util.ArrayList<>(List.of(line)))
                .build();
        line.setOrder(order);

        table = RestaurantTable.builder().id(5L).tableNumber("T-05").build();

        // lenient: the reject-before-save paths (UT-12, UT-14, order-not-found) never
        // reach these calls, so strict stubbing would otherwise flag them as unused.
        lenient().when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(tableRepository.findById(5L)).thenReturn(Optional.of(table));
    }

    @Test
    @DisplayName("UT-11: Legal transition PENDING -> PREPARING updates status and sets preparingStartedAt")
    void legalTransition_pendingToPreparing() {
        when(orderRepository.findWithItemsById(500L)).thenReturn(order);

        Order result = orderService.updateStatus(500L, OrderStatus.PREPARING, null);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.PREPARING);
        assertThat(result.getPreparingStartedAt()).isNotNull();
        assertThat(result.getItems().get(0).getLineStatus()).isEqualTo(OrderStatus.PREPARING);
        verify(publisher).publishLineStatusChanged(result, "T-05");
    }

    @Test
    @DisplayName("UT-12: Illegal transition PENDING -> COMPLETED is rejected")
    void illegalTransition_pendingToCompleted() {
        when(orderRepository.findWithItemsById(500L)).thenReturn(order);

        assertThatThrownBy(() -> orderService.updateStatus(500L, OrderStatus.COMPLETED, null))
                .isInstanceOf(InvalidOrderStateException.class);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING); // unchanged
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("UT-13: Voiding a PENDING order releases the table and broadcasts the void, no manager approval needed")
    void voidOrder_releasesTable() {
        when(orderRepository.findWithItemsById(500L)).thenReturn(order);

        Order result = orderService.voidOrder(500L, null, null);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.VOID);
        verify(tableService).releaseTable(5L);
        verify(publisher).publishOrderVoided(result, "T-05");
        verify(managerAuthorizationService, never()).requireManagerApproval(any(), any());
    }

    @Test
    @DisplayName("UT-14: Voiding an already-COMPLETED order is rejected")
    void voidOrder_completedOrder_rejected() {
        order.setStatus(OrderStatus.COMPLETED);
        when(orderRepository.findWithItemsById(500L)).thenReturn(order);

        assertThatThrownBy(() -> orderService.voidOrder(500L, null, null))
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessageContaining("COMPLETED");

        verify(tableService, never()).releaseTable(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("Voiding a READY order is rejected - VOID is only reachable from PENDING/PREPARING")
    void voidOrder_readyOrder_rejected() {
        order.setStatus(OrderStatus.READY);
        when(orderRepository.findWithItemsById(500L)).thenReturn(order);

        assertThatThrownBy(() -> orderService.voidOrder(500L, null, null))
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessageContaining("READY");

        verify(tableService, never()).releaseTable(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("Voiding a PREPARING order requires manager approval, which is delegated correctly")
    void voidOrder_preparingOrder_requiresManagerApproval() {
        order.setStatus(OrderStatus.PREPARING);
        when(orderRepository.findWithItemsById(500L)).thenReturn(order);

        Order result = orderService.voidOrder(500L, "dilshan", "manager123");

        assertThat(result.getStatus()).isEqualTo(OrderStatus.VOID);
        verify(managerAuthorizationService).requireManagerApproval("dilshan", "manager123");
        verify(tableService).releaseTable(5L);
    }

    @Test
    @DisplayName("Voiding a PREPARING order without manager credentials is rejected before any mutation")
    void voidOrder_preparingOrder_missingApproval_rejected() {
        order.setStatus(OrderStatus.PREPARING);
        when(orderRepository.findWithItemsById(500L)).thenReturn(order);
        doThrow(new InvalidOrderStateException("Manager approval is required for this action"))
                .when(managerAuthorizationService).requireManagerApproval(null, null);

        assertThatThrownBy(() -> orderService.voidOrder(500L, null, null))
                .isInstanceOf(InvalidOrderStateException.class);

        verify(tableService, never()).releaseTable(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("Completing an order hands the table to housekeeping (CLEANING), not straight to AVAILABLE")
    void updateStatus_completedOrder_movesTableToCleaning() {
        order.setStatus(OrderStatus.BILLED);
        order.getItems().get(0).setLineStatus(OrderStatus.BILLED);
        when(orderRepository.findWithItemsById(500L)).thenReturn(order);

        orderService.updateStatus(500L, OrderStatus.COMPLETED, null);

        verify(tableService).markCleaning(5L);
        verify(tableService, never()).markBilled(any());
    }

    @Test
    @DisplayName("updateStatus on a non-existent order raises ResourceNotFoundException")
    void updateStatus_orderNotFound() {
        when(orderRepository.findWithItemsById(999L)).thenReturn(null);

        assertThatThrownBy(() -> orderService.updateStatus(999L, OrderStatus.PREPARING, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Order transitions to READY only once every line has reached READY")
    void updateStatus_partialLinesReady_orderStaysAtPreviousStatus() {
        OrderDetail secondLine = OrderDetail.builder().id(101L).quantity(1).lineStatus(OrderStatus.PREPARING).build();
        secondLine.setOrder(order);
        order.getItems().add(secondLine);
        order.setStatus(OrderStatus.PREPARING);
        order.getItems().get(0).setLineStatus(OrderStatus.PREPARING);

        when(orderRepository.findWithItemsById(500L)).thenReturn(order);

        // Mark only the FIRST line ready; the second line is still PREPARING.
        Order result = orderService.updateStatus(500L, OrderStatus.READY, 100L);

        assertThat(result.getItems().get(0).getLineStatus()).isEqualTo(OrderStatus.READY);
        assertThat(result.getItems().get(1).getLineStatus()).isEqualTo(OrderStatus.PREPARING);
        // Parent order stays PREPARING - not every line has caught up to READY yet.
        assertThat(result.getStatus()).isEqualTo(OrderStatus.PREPARING);
        verify(publisher, never()).notifyWaiterOrderReady(any(), any(), any());
    }
}
