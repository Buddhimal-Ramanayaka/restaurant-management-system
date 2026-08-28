package com.rms.service;

import com.rms.aspect.AuditableAction;
import com.rms.domain.*;
import com.rms.domain.enums.OrderStatus;
import com.rms.dto.response.BillLineResponse;
import com.rms.dto.response.BillResponse;
import com.rms.dto.response.ShiftResponse;
import com.rms.exception.InvalidOrderStateException;
import com.rms.exception.ResourceNotFoundException;
import com.rms.repository.OrderRepository;
import com.rms.repository.ShiftRepository;
import com.rms.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Module 2.5 - Billing and Shift reconciliation. Kept deliberately separate from
 * OrderService: billing computes money, OrderService moves state - mixing the two
 * would make it harder to reason about which class is responsible for a given bug.
 *
 * KNOWN SIMPLIFICATION: line pricing uses MenuItem.getPrice() AT THE TIME THE BILL
 * IS COMPUTED, not a price snapshotted when the order was originally placed. For a
 * single-location SME restaurant where price changes are infrequent and always
 * announced in advance, this is an acceptable approximation; a production system
 * handling frequent price changes mid-shift should add a unitPriceAtOrder column to
 * order_details and snapshot it in OrderService.submitOrder instead. Flagged here
 * rather than silently assumed - see Chapter 6 future work in the dissertation.
 */
@Service
@RequiredArgsConstructor
public class BillingService {

    private final OrderRepository orderRepository;
    private final PromotionService promotionService;
    private final ShiftRepository shiftRepository;
    private final UserRepository userRepository;
    private final OrderService orderService;
    private final AuthenticationManager authenticationManager;
    private final PdfExportService pdfExportService;
    private final SettingsService settingsService;

    private static final Set<String> DISCOUNT_AUTHORIZED_ROLES = Set.of("MANAGER", "ADMIN");

    @Transactional(readOnly = true)
    public BillResponse computeBill(Long orderId) {
        Order order = orderRepository.findWithItemsById(orderId);
        if (order == null) {
            throw new ResourceNotFoundException("Order not found: " + orderId);
        }
        return buildBill(order, null);
    }

    /** FR-23 / Appendix B.3 - "Print Bill" as a real downloadable PDF, not just browser print. */
    @Transactional(readOnly = true)
    public byte[] generateBillPdf(Long orderId) {
        Order order = orderRepository.findWithItemsById(orderId);
        if (order == null) {
            throw new ResourceNotFoundException("Order not found: " + orderId);
        }
        BillResponse bill = buildBill(order, null);
        return pdfExportService.renderBill(order.getId(), order.getTableId(), bill);
    }

    /**
     * FR-22 - a manual discount requires live re-authentication as a Manager or Admin,
     * checked here via the same AuthenticationManager a login uses (no token is issued
     * for the manager; this call only proves "a manager approved this, right now").
     * Nothing is persisted by this call - it is a priced preview, exactly like
     * computeBill - the discount only becomes real once settleOrder is called with the
     * same percentage. The audit entry is what makes the authorization step provable
     * after the fact, per FR-22's "logged in the audit trail" requirement.
     *
     * NOT readOnly, despite this method never writing itself: @AuditableAction's aspect
     * always performs a write (in its own REQUIRES_NEW transaction) right after this
     * method returns, and in this Spring+HikariCP+MySQL stack that nested write inherited
     * the outer read-only connection hint and failed with "Connection is read-only" -
     * every other @AuditableAction method in this codebase writes itself and was never
     * marked readOnly, which is why this combination hadn't surfaced before.
     */
    @Transactional
    @AuditableAction("MANUAL_DISCOUNT_APPLIED")
    public BillResponse applyManualDiscount(Long orderId, String managerUsername, String managerPassword, BigDecimal discountPercent) {
        UserPrincipalRole authorized = authenticateAndCheckRole(managerUsername, managerPassword);
        if (!DISCOUNT_AUTHORIZED_ROLES.contains(authorized.role())) {
            throw new InvalidOrderStateException("User " + managerUsername + " is not authorized to approve a discount");
        }

        Order order = orderRepository.findWithItemsById(orderId);
        if (order == null) {
            throw new ResourceNotFoundException("Order not found: " + orderId);
        }
        return buildBill(order, discountPercent);
    }

    private record UserPrincipalRole(String role) {}

    private UserPrincipalRole authenticateAndCheckRole(String username, String password) {
        try {
            var authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password));
            String role = authentication.getAuthorities().stream()
                    .findFirst()
                    .map(a -> a.getAuthority().replace("ROLE_", ""))
                    .orElse("");
            return new UserPrincipalRole(role);
        } catch (BadCredentialsException ex) {
            throw new InvalidOrderStateException("Manager credentials could not be verified");
        }
    }

    /** manualDiscountPercent, when present, overrides automatic promotion matching entirely - a
     *  manager-authorized override takes precedence rather than stacking with a loyalty promo. */
    private BillResponse buildBill(Order order, BigDecimal manualDiscountPercent) {
        BigDecimal subtotal = BigDecimal.ZERO;
        List<BillLineResponse> lines = new java.util.ArrayList<>();

        for (OrderDetail detail : order.getItems()) {
            BigDecimal unitPrice = detail.getMenuItem().getPrice();
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(detail.getQuantity()));
            lines.add(new BillLineResponse(detail.getMenuItem().getName(), detail.getQuantity(), unitPrice, BigDecimal.ZERO, lineTotal));
            subtotal = subtotal.add(lineTotal);
        }

        BigDecimal discount = BigDecimal.ZERO;
        String promotionName = null;

        if (manualDiscountPercent != null) {
            discount = subtotal.multiply(manualDiscountPercent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            promotionName = "Manual discount (" + manualDiscountPercent.stripTrailingZeros().toPlainString() + "%)";
        } else {
            String tier = order.getCustomer() != null ? order.getCustomer().getLoyaltyTier() : null;
            Optional<Promotion> promotion = promotionService.findApplicable(tier);
            if (promotion.isPresent()) {
                discount = subtotal.multiply(promotion.get().getDiscountPercent())
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                promotionName = promotion.get().getName();
            }
        }

        // Discount is applied BEFORE service charge / VAT - see FR-21 in the spec. Both rates
        // are Admin-configurable (SettingsService), not hardcoded - see FR-21's own "configurable
        // by Admin" clause, which a plain literal here would not satisfy.
        BigDecimal discountedSubtotal = subtotal.subtract(discount);
        BigDecimal serviceCharge = discountedSubtotal.multiply(settingsService.getServiceChargeRate()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal vat = discountedSubtotal.add(serviceCharge).multiply(settingsService.getVatRate()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = discountedSubtotal.add(serviceCharge).add(vat);

        return new BillResponse(order.getId(), lines, subtotal, discount, serviceCharge, vat, total, promotionName);
    }

    /**
     * Settles an order: advances BILLED -> COMPLETED via OrderService rules (assumed
     * already at BILLED when this is called), records the tendered amount against the
     * cashier active shift running total, and returns the final bill for receipt printing.
     * manualDiscountPercent, if present, must equal what applyManualDiscount already
     * showed the cashier - recomputed here rather than trusted from the preview response,
     * so the persisted totals always come from this method's own arithmetic.
     */
    @Transactional
    public BillResponse settleOrder(Long orderId, Long shiftId, String paymentMethod, BigDecimal amount, BigDecimal manualDiscountPercent) {
        Order order = orderRepository.findWithItemsById(orderId);
        if (order == null) {
            throw new ResourceNotFoundException("Order not found: " + orderId);
        }
        if (order.getStatus() != OrderStatus.BILLED) {
            throw new InvalidOrderStateException("Order must be BILLED before it can be settled (currently " + order.getStatus() + ")");
        }

        recordPayment(shiftId, paymentMethod, amount);

        // Delegates the actual state transition to OrderService - the only class that
        // may mutate Order.status - so this WebSocket broadcast and the legal-transition
        // check are never bypassed just because the call originated from Billing rather
        // than the Kitchen or POS flow.
        Order updated = orderService.updateStatus(order.getId(), OrderStatus.COMPLETED, null);

        return buildBill(updated, manualDiscountPercent);
    }
// When Billing, add a extra items for the bill and that should be added to the total bill and saved in database. extra items such as water bottles

    @Transactional
    public ShiftResponse startShift(Long cashierId) {
        User cashier = userRepository.findById(cashierId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + cashierId));
        Shift shift = Shift.builder()
                .cashier(cashier)
                .startedAt(LocalDateTime.now())
                .systemCashTotal(BigDecimal.ZERO)
                .systemCardTotal(BigDecimal.ZERO)
                .systemDigitalTotal(BigDecimal.ZERO)
                .build();
        return ShiftResponse.from(shiftRepository.save(shift));
    }

    /** Lets the Cashier Billing Terminal resume an in-progress shift after a page reload. */
    @Transactional(readOnly = true)
    public Optional<ShiftResponse> findActiveShiftForCashier(Long cashierId) {
        return shiftRepository.findTopByCashierIdAndEndedAtIsNullOrderByStartedAtDesc(cashierId)
                .map(ShiftResponse::from);
    }

    @Transactional
    public Shift recordPayment(Long shiftId, String paymentMethod, BigDecimal amount) {
        Shift shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new ResourceNotFoundException("Shift not found: " + shiftId));

        switch (paymentMethod.toUpperCase()) {
            case "CASH" -> shift.setSystemCashTotal(nz(shift.getSystemCashTotal()).add(amount));
            case "CARD" -> shift.setSystemCardTotal(nz(shift.getSystemCardTotal()).add(amount));
            case "DIGITAL" -> shift.setSystemDigitalTotal(nz(shift.getSystemDigitalTotal()).add(amount));
            default -> throw new IllegalArgumentException("Unknown payment method: " + paymentMethod);
        }
        return shiftRepository.save(shift);
    }

    @Transactional
    @AuditableAction("SHIFT_CLOSED")
    public ShiftResponse closeShift(Long shiftId, BigDecimal declaredDrawerAmount) {
        Shift shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new ResourceNotFoundException("Shift not found: " + shiftId));

        shift.setEndedAt(LocalDateTime.now());
        shift.setDeclaredDrawerAmount(declaredDrawerAmount);
        shift.setVariance(declaredDrawerAmount.subtract(nz(shift.getSystemCashTotal())));

        return ShiftResponse.from(shiftRepository.save(shift));
    }

    /** Manager Dashboard "Review Shift Reports" use case (Figure 2.1) / OBJ-09 shift variance reconciliation. */
    @Transactional(readOnly = true)
    public List<ShiftResponse> findClosedShifts() {
        return shiftRepository.findByEndedAtIsNotNullOrderByEndedAtDesc().stream()
                .map(ShiftResponse::from)
                .collect(java.util.stream.Collectors.toList());
    }

    @Transactional
    @AuditableAction("SHIFT_REVIEWED")
    public ShiftResponse reviewShift(Long shiftId, Long managerId) {
        Shift shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new ResourceNotFoundException("Shift not found: " + shiftId));
        if (shift.getEndedAt() == null) {
            throw new InvalidOrderStateException("Shift " + shiftId + " is still open and cannot be reviewed yet");
        }
        User manager = userRepository.findById(managerId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + managerId));
        shift.setReviewedBy(manager);
        return ShiftResponse.from(shiftRepository.save(shift));
    }

    private BigDecimal nz(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
