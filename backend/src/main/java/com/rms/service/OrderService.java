package com.rms.service;

import com.rms.aspect.AuditableAction;
import com.rms.domain.*;
import com.rms.domain.enums.OrderStatus;
import com.rms.dto.request.CreateOrderRequest;
import com.rms.dto.request.OrderItemRequest;
import com.rms.exception.InvalidOrderStateException;
import com.rms.exception.ResourceNotFoundException;
import com.rms.repository.*;
import com.rms.security.UserPrincipal;
import com.rms.websocket.OrderEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates the POS -> Kitchen -> Cashier lifecycle of an order. This is the class
 * that ties the Recipe Deduction Engine, the Table state machine, and the WebSocket
 * signalling layer together into one transactional unit per API call.
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final MenuItemRepository menuItemRepository;
    private final UserRepository userRepository;
    private final RestaurantTableRepository tableRepository;
    private final CustomerRepository customerRepository;
    private final RecipeDeductionService recipeDeductionService;
    private final TableService tableService;
    private final OrderEventPublisher publisher;
    private final ManagerAuthorizationService managerAuthorizationService;

    /**
     * The Module 2.2 "SUBMITTED" trigger, concretely: this method is the ONLY entry
     * point that creates an Order, and it deducts stock in the same transaction as the
     * insert (single @Transactional boundary for both). If deduction throws
     * InsufficientStockException, Spring rolls back the whole method - no order row,
     * no table lock, no partially-decremented ingredients survive.
     */
    @Transactional
    public Order submitOrder(CreateOrderRequest request, UserPrincipal waiterPrincipal) {
        User waiter = userRepository.findById(waiterPrincipal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Waiter not found"));

        // Locks + validates + flips the table to OCCUPIED - see TableService for the
        // state machine rules (a BILLED or CLEANING table cannot accept a new order).
        RestaurantTable table = tableService.openTableForOrder(request.tableId());

        Order order = Order.builder()
                .tableId(table.getId())
                .waiter(waiter)
                .status(OrderStatus.PENDING)
                .build();

        if (request.customerPhone() != null && !request.customerPhone().isBlank()) {
            Customer customer = customerRepository.findByPhoneNumber(request.customerPhone())
                    .orElseGet(() -> customerRepository.save(Customer.builder()
                            .name("Walk-in")
                            .phoneNumber(request.customerPhone())
                            .build()));
            order.setCustomer(customer);
        }

        List<RecipeDeductionService.DeductionLine> deductionLines = request.items().stream()
                .map(this::toDeductionLine)
                .toList();

        for (OrderItemRequest itemRequest : request.items()) {
            MenuItem menuItem = menuItemRepository.findById(itemRequest.menuItemId())
                    .orElseThrow(() -> new ResourceNotFoundException("Menu item not found: " + itemRequest.menuItemId()));

            if (!Boolean.TRUE.equals(menuItem.getIsAvailable())) {
                throw new InvalidOrderStateException(menuItem.getName() + " is currently unavailable");
            }

            order.addItem(OrderDetail.builder()
                    .menuItem(menuItem)
                    .quantity(itemRequest.quantity())
                    .specialNotes(itemRequest.specialNotes())
                    .lineStatus(OrderStatus.PENDING)
                    .build());
        }

        // Deduct stock BEFORE the final save so a shortfall aborts the whole transaction
        // and the table-open above rolls back along with it (see TableService note).
        recipeDeductionService.deductForOrder(deductionLines);

        Order saved = orderRepository.save(order);
        table.setCurrentOrderId(saved.getId());

        publisher.publishNewTicket(saved, table.getTableNumber());
        publisher.publishTableStatusChanged(com.rms.dto.response.TableResponse.from(table));

        return saved;
    }

    private RecipeDeductionService.DeductionLine toDeductionLine(OrderItemRequest req) {
        return new RecipeDeductionService.DeductionLine(req.menuItemId(), req.quantity());
    }

    /**
     * Moves either a single line (orderDetailId present) or every remaining line in the
     * order to newStatus. The parent Order.status only advances once every line has
     * reached at least that status - this is what lets the Kitchen Kanban move
     * individual dishes across lanes independently while the POS/Cashier views still see
     * one coherent order-level status.
     */
    @Transactional
    public Order updateStatus(Long orderId, OrderStatus newStatus, Long orderDetailId) {
        Order order = orderRepository.findWithItemsById(orderId);
        if (order == null) {
            throw new ResourceNotFoundException("Order not found: " + orderId);
        }

        assertLegalTransition(order.getStatus(), newStatus);

        if (orderDetailId != null) {
            OrderDetail target = order.getItems().stream()
                    .filter(i -> i.getId().equals(orderDetailId))
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("Order line not found: " + orderDetailId));
            target.setLineStatus(newStatus);
        } else {
            order.getItems().forEach(i -> i.setLineStatus(newStatus));
        }

        if (newStatus == OrderStatus.PREPARING && order.getPreparingStartedAt() == null) {
            order.setPreparingStartedAt(LocalDateTime.now());
        }

        boolean allLinesAtLeast = order.getItems().stream()
                .allMatch(i -> i.getLineStatus().ordinal() >= newStatus.ordinal());
        if (allLinesAtLeast) {
            order.setStatus(newStatus);
        }

        Order saved = orderRepository.save(order);

        RestaurantTable table = tableRepository.findById(order.getTableId())
                .orElseThrow(() -> new ResourceNotFoundException("Table not found"));

        publisher.publishLineStatusChanged(saved, table.getTableNumber());

        if (saved.getStatus() == OrderStatus.READY) {
            publisher.notifyWaiterOrderReady(saved.getWaiter().getUsername(), saved.getId(), table.getTableNumber());
        }

        if (saved.getStatus() == OrderStatus.BILLED) {
            tableService.markBilled(table.getId());
        }

        // Payment settled: hand the table to housekeeping rather than leaving it BILLED
        // indefinitely - see Chapter 3 Table Status state machine (BILLED -> CLEANING on
        // payment confirmation, never straight back to AVAILABLE).
        if (saved.getStatus() == OrderStatus.COMPLETED) {
            tableService.markCleaning(table.getId());
        }

        return saved;
    }

    /**
     * Appendix B: a PENDING order (kitchen hasn't started it) can be voided by the
     * waiter directly. An order already in PREPARING has consumed ingredient stock via
     * the Recipe Deduction Engine and the kitchen is actively working it, so voiding it
     * requires live Manager/Admin credential verification - managerUsername/Password
     * are ignored for the PENDING case.
     */
    @Transactional
    @AuditableAction("ORDER_VOIDED")
    public Order voidOrder(Long orderId, String managerUsername, String managerPassword) {
        Order order = orderRepository.findWithItemsById(orderId);
        if (order == null) {
            throw new ResourceNotFoundException("Order not found: " + orderId);
        }
        // State machine (Chapter 3.6): VOID is a terminal side-branch reachable only from
        // PENDING or PREPARING - once the kitchen marks an order READY it has committed
        // the finished dish, so it must be delivered and billed like any other order.
        if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.PREPARING) {
            throw new InvalidOrderStateException("Cannot void an order that is already " + order.getStatus());
        }
        if (order.getStatus() == OrderStatus.PREPARING) {
            managerAuthorizationService.requireManagerApproval(managerUsername, managerPassword);
        }
        order.setStatus(OrderStatus.VOID);
        Order saved = orderRepository.save(order);

        RestaurantTable table = tableRepository.findById(order.getTableId())
                .orElseThrow(() -> new ResourceNotFoundException("Table not found"));
        tableService.releaseTable(table.getId());
        publisher.publishOrderVoided(saved, table.getTableNumber());

        return saved;
    }

    @Transactional(readOnly = true)
    public List<Order> findActiveForKitchen() {
        return orderRepository.findByStatusInOrderByCreatedAtAsc(
                List.of(OrderStatus.PENDING, OrderStatus.PREPARING, OrderStatus.READY));
    }

    /** Cashier Billing Terminal queue: kitchen has finished (READY) or billing already opened (BILLED). */
    @Transactional(readOnly = true)
    public List<Order> findAwaitingBilling() {
        return orderRepository.findByStatusInOrderByCreatedAtAsc(
                List.of(OrderStatus.READY, OrderStatus.BILLED));
    }

    /**
     * Resolves the owning order for a bare orderDetailId and delegates to updateStatus. This
     * is the entry point KitchenWebSocketController uses: the Kitchen Kanban card only knows
     * the line it is rendering, not the parent order id, so this method does the lookup the
     * REST endpoint's path variable would otherwise supply.
     */
    @Transactional
    public Order updateStatusByLineId(Long orderDetailId, OrderStatus newStatus) {
        OrderDetail detail = orderDetailRepository.findById(orderDetailId)
                .orElseThrow(() -> new ResourceNotFoundException("Order line not found: " + orderDetailId));
        return updateStatus(detail.getOrder().getId(), newStatus, orderDetailId);
    }

    /** PENDING -> PREPARING -> READY -> BILLED -> COMPLETED is the only forward path; VOID is handled separately. */
    private static final Map<OrderStatus, OrderStatus> NEXT = Map.of(
            OrderStatus.PENDING, OrderStatus.PREPARING,
            OrderStatus.PREPARING, OrderStatus.READY,
            OrderStatus.READY, OrderStatus.BILLED,
            OrderStatus.BILLED, OrderStatus.COMPLETED
    );

    private void assertLegalTransition(OrderStatus current, OrderStatus target) {
        if (current == OrderStatus.VOID || current == OrderStatus.COMPLETED) {
            throw new InvalidOrderStateException("Order is already terminal (" + current + ")");
        }
        // Allow re-affirming the same status (e.g. a second line reaching PREPARING)
        // and allow the single legal forward step; anything else is rejected.
        if (target != current && !target.equals(NEXT.get(current))) {
            throw new InvalidOrderStateException("Cannot move order from " + current + " to " + target);
        }
    }
}
