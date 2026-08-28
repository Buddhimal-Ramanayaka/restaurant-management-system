package com.rms.controller;

import com.rms.dto.request.ApplyManualDiscountRequest;
import com.rms.dto.request.CloseShiftRequest;
import com.rms.dto.request.RecordPaymentRequest;
import com.rms.dto.response.BillResponse;
import com.rms.dto.response.ShiftResponse;
import com.rms.exception.ResourceNotFoundException;
import com.rms.security.UserPrincipal;
import com.rms.service.BillingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/billing")
@RequiredArgsConstructor
public class BillingController {

    private final BillingService billingService;

    /** Non-destructive preview - the cashier can pull this up before committing to settlement. */
    @GetMapping("/orders/{orderId}/bill")
    public ResponseEntity<BillResponse> previewBill(@PathVariable Long orderId) {
        return ResponseEntity.ok(billingService.computeBill(orderId));
    }

    /** FR-23 - the bill as a real downloadable PDF, priced with the automatic promotion only
     *  (a settled order's discount is whatever was actually applied at settlement time). */
    @GetMapping("/orders/{orderId}/bill/pdf")
    public ResponseEntity<byte[]> downloadBillPdf(@PathVariable Long orderId) {
        byte[] pdf = billingService.generateBillPdf(orderId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename("order-" + orderId + "-bill.pdf").build().toString())
                .body(pdf);
    }

    /** FR-22 - re-verifies a Manager/Admin's own credentials before pricing the discount in. */
    @PostMapping("/orders/{orderId}/manual-discount")
    public ResponseEntity<BillResponse> applyManualDiscount(
            @PathVariable Long orderId, @Valid @RequestBody ApplyManualDiscountRequest request
    ) {
        BillResponse bill = billingService.applyManualDiscount(
                orderId, request.managerUsername(), request.managerPassword(), request.discountPercent());
        return ResponseEntity.ok(bill);
    }

    @PostMapping("/orders/{orderId}/settle")
    public ResponseEntity<BillResponse> settle(
            @PathVariable Long orderId,
            @Valid @RequestBody RecordPaymentRequest request
    ) {
        BillResponse bill = billingService.settleOrder(
                orderId, request.shiftId(), request.paymentMethod(), request.amount(), request.manualDiscountPercent());
        return ResponseEntity.ok(bill);
    }

    @PostMapping("/shifts/start")
    public ResponseEntity<ShiftResponse> startShift(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(billingService.startShift(principal.getId()));
    }

    /** Lets the Cashier Billing Terminal resume an in-progress shift after a page reload. */
    @GetMapping("/shifts/active")
    public ResponseEntity<ShiftResponse> findActiveShift(@AuthenticationPrincipal UserPrincipal principal) {
        return billingService.findActiveShiftForCashier(principal.getId())
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException("No active shift for user " + principal.getId()));
    }

    @PostMapping("/shifts/{id}/close")
    public ResponseEntity<ShiftResponse> closeShift(
            @PathVariable Long id, @Valid @RequestBody CloseShiftRequest request
    ) {
        return ResponseEntity.ok(billingService.closeShift(id, request.declaredDrawerAmount()));
    }

    /** Manager Dashboard "Review Shift Reports" use case - closed shifts, most recent first. */
    @GetMapping("/shifts")
    public ResponseEntity<List<ShiftResponse>> findClosedShifts() {
        return ResponseEntity.ok(billingService.findClosedShifts());
    }

    @PostMapping("/shifts/{id}/review")
    public ResponseEntity<ShiftResponse> reviewShift(
            @PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(billingService.reviewShift(id, principal.getId()));
    }
}
