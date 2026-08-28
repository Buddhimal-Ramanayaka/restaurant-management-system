package com.rms.controller;

import com.rms.domain.enums.PurchaseOrderStatus;
import com.rms.dto.request.CreatePurchaseOrderRequest;
import com.rms.dto.response.PurchaseOrderResponse;
import com.rms.security.UserPrincipal;
import com.rms.service.PurchaseOrderService;
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
@RequestMapping("/api/purchase-orders")
@RequiredArgsConstructor
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;

    @GetMapping
    public ResponseEntity<List<PurchaseOrderResponse>> findByStatus(@RequestParam PurchaseOrderStatus status) {
        return ResponseEntity.ok(purchaseOrderService.findByStatus(status));
    }

    /** Manager Dashboard PO approval panel: every PO still awaiting action, across all non-terminal statuses. */
    @GetMapping("/active")
    public ResponseEntity<List<PurchaseOrderResponse>> findActionable() {
        return ResponseEntity.ok(purchaseOrderService.findActionable());
    }

    @PostMapping
    public ResponseEntity<PurchaseOrderResponse> create(@Valid @RequestBody CreatePurchaseOrderRequest request) {
        return ResponseEntity.ok(purchaseOrderService.createManual(request));
    }

    /** Moves the PO exactly one step forward in its lifecycle; see PurchaseOrderService.NEXT. */
    @PatchMapping("/{id}/advance")
    public ResponseEntity<PurchaseOrderResponse> advance(
            @PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(purchaseOrderService.advance(id, principal.getId()));
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<PurchaseOrderResponse> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(purchaseOrderService.cancel(id));
    }

    /** Appendix B.4 - "export them as PDF documents for email to suppliers". */
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable Long id) {
        byte[] pdf = purchaseOrderService.generatePdf(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename("purchase-order-" + id + ".pdf").build().toString())
                .body(pdf);
    }
}
