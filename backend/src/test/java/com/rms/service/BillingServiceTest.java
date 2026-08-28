package com.rms.service;

import com.rms.domain.*;
import com.rms.domain.enums.OrderStatus;
import com.rms.dto.response.BillResponse;
import com.rms.repository.OrderRepository;
import com.rms.repository.ShiftRepository;
import com.rms.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit test for BillingService's bill computation. Mirrors UT-18 from the
 * dissertation Chapter 5 evaluation table: a 1000-unit order with a 10% loyalty
 * discount, 10% service charge, and 8% VAT, verifying the discount is applied
 * BEFORE service charge and VAT are computed (FR-21).
 */
@ExtendWith(MockitoExtension.class)
class BillingServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private PromotionService promotionService;
    @Mock private ShiftRepository shiftRepository;
    @Mock private UserRepository userRepository;
    @Mock private OrderService orderService;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private PdfExportService pdfExportService;
    @Mock private SettingsService settingsService;

    private BillingService billingService;

    @BeforeEach
    void setUp() {
        billingService = new BillingService(
                orderRepository, promotionService, shiftRepository, userRepository, orderService,
                authenticationManager, pdfExportService, settingsService);

        // FR-21: rates are now Admin-configurable rather than hardcoded - stub the default
        // 10%/8% values so the bill-computation tests' arithmetic assertions (written against
        // those defaults) keep meaning what they say. lenient(): the shift-review tests below
        // never call buildBill, so strict stubbing would otherwise flag these as unused.
        lenient().when(settingsService.getServiceChargeRate()).thenReturn(new BigDecimal("0.10"));
        lenient().when(settingsService.getVatRate()).thenReturn(new BigDecimal("0.08"));
    }

    @Test
    @DisplayName("UT-18: 10% loyalty discount applied before service charge and VAT")
    void computeBill_discountAppliedBeforeTax() {
        Customer goldCustomer = Customer.builder().id(1L).name("Thilini").phoneNumber("0771234567").loyaltyTier("GOLD").build();

        MenuItem item = MenuItem.builder().id(1L).name("Chicken Biryani").price(new BigDecimal("1000")).build();
        OrderDetail line = OrderDetail.builder().menuItem(item).quantity(1).build();

        Order order = Order.builder()
                .id(900L).status(OrderStatus.READY).customer(goldCustomer)
                .items(List.of(line))
                .build();

        Promotion goldDiscount = Promotion.builder()
                .id(1L).name("Gold Tier 10% Off").discountPercent(new BigDecimal("10"))
                .requiredLoyaltyTier("GOLD").enabled(true)
                .build();

        when(orderRepository.findWithItemsById(900L)).thenReturn(order);
        when(promotionService.findApplicable("GOLD")).thenReturn(java.util.Optional.of(goldDiscount));

        BillResponse bill = billingService.computeBill(900L);

        assertThat(bill.subtotal()).isEqualByComparingTo("1000");
        assertThat(bill.totalDiscount()).isEqualByComparingTo("100.00");
        assertThat(bill.serviceCharge()).isEqualByComparingTo("90.00");   // 10% of (1000-100)
        assertThat(bill.vat()).isEqualByComparingTo("79.20");             // 8% of (900+90)
        assertThat(bill.total()).isEqualByComparingTo("1069.20");
        assertThat(bill.appliedPromotionName()).isEqualTo("Gold Tier 10% Off");
    }

    @Test
    @DisplayName("No matching promotion (wrong loyalty tier) leaves the bill undiscounted")
    void computeBill_noMatchingPromotion_noDiscount() {
        Customer standardCustomer = Customer.builder().id(2L).name("Walk-in").phoneNumber("0779999999").loyaltyTier("STANDARD").build();

        MenuItem item = MenuItem.builder().id(1L).name("Garlic Naan").price(new BigDecimal("80")).build();
        OrderDetail line = OrderDetail.builder().menuItem(item).quantity(3).build();

        Order order = Order.builder().id(901L).status(OrderStatus.READY).customer(standardCustomer).items(List.of(line)).build();

        when(orderRepository.findWithItemsById(901L)).thenReturn(order);
        when(promotionService.findApplicable("STANDARD")).thenReturn(java.util.Optional.empty());

        BillResponse bill = billingService.computeBill(901L);

        assertThat(bill.subtotal()).isEqualByComparingTo("240"); // 80 * 3
        assertThat(bill.totalDiscount()).isEqualByComparingTo("0");
        assertThat(bill.appliedPromotionName()).isNull();
    }

    @Test
    @DisplayName("findClosedShifts returns only ended shifts, most recently ended first, mapped with reviewer")
    void findClosedShifts_mapsReviewerUsername() {
        User cashier = User.builder().id(5L).username("cashier").build();
        User manager = User.builder().id(1L).username("dilshan").build();

        Shift reviewed = Shift.builder()
                .id(10L).cashier(cashier).startedAt(java.time.LocalDateTime.now().minusHours(9))
                .endedAt(java.time.LocalDateTime.now().minusHours(1))
                .systemCashTotal(new BigDecimal("3564.00")).declaredDrawerAmount(new BigDecimal("3564.00"))
                .variance(BigDecimal.ZERO).reviewedBy(manager)
                .build();
        Shift unreviewed = Shift.builder()
                .id(11L).cashier(cashier).startedAt(java.time.LocalDateTime.now().minusHours(8))
                .endedAt(java.time.LocalDateTime.now())
                .systemCashTotal(BigDecimal.ZERO).declaredDrawerAmount(new BigDecimal("100.00"))
                .variance(new BigDecimal("100.00"))
                .build();

        when(shiftRepository.findByEndedAtIsNotNullOrderByEndedAtDesc()).thenReturn(List.of(unreviewed, reviewed));

        List<com.rms.dto.response.ShiftResponse> result = billingService.findClosedShifts();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).id()).isEqualTo(11L);
        assertThat(result.get(0).reviewedByUsername()).isNull();
        assertThat(result.get(1).id()).isEqualTo(10L);
        assertThat(result.get(1).reviewedByUsername()).isEqualTo("dilshan");
    }

    @Test
    @DisplayName("reviewShift stamps the reviewing manager onto a closed shift")
    void reviewShift_setsReviewer() {
        User cashier = User.builder().id(5L).username("cashier").build();
        User manager = User.builder().id(1L).username("dilshan").build();
        Shift shift = Shift.builder()
                .id(10L).cashier(cashier).startedAt(java.time.LocalDateTime.now().minusHours(9))
                .endedAt(java.time.LocalDateTime.now())
                .systemCashTotal(new BigDecimal("3564.00")).declaredDrawerAmount(new BigDecimal("3564.00"))
                .variance(BigDecimal.ZERO)
                .build();

        when(shiftRepository.findById(10L)).thenReturn(java.util.Optional.of(shift));
        when(userRepository.findById(1L)).thenReturn(java.util.Optional.of(manager));
        when(shiftRepository.save(shift)).thenReturn(shift);

        com.rms.dto.response.ShiftResponse result = billingService.reviewShift(10L, 1L);

        assertThat(result.reviewedByUsername()).isEqualTo("dilshan");
        assertThat(shift.getReviewedBy()).isEqualTo(manager);
    }

    @Test
    @DisplayName("reviewShift rejects a shift that is still open")
    void reviewShift_openShift_rejected() {
        User cashier = User.builder().id(5L).username("cashier").build();
        Shift openShift = Shift.builder().id(12L).cashier(cashier).startedAt(java.time.LocalDateTime.now()).build();

        when(shiftRepository.findById(12L)).thenReturn(java.util.Optional.of(openShift));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> billingService.reviewShift(12L, 1L))
                .isInstanceOf(com.rms.exception.InvalidOrderStateException.class);
    }
}
