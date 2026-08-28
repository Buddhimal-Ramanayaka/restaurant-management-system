package com.rms.controller;

import com.rms.dto.request.UpdateBillingRatesRequest;
import com.rms.dto.response.BillingRatesResponse;
import com.rms.service.SettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;

    /** Read access is broader than write: the Waiter POS cart needs these to preview a live
     *  total that matches what the Cashier will actually bill. */
    @GetMapping("/billing-rates")
    public ResponseEntity<BillingRatesResponse> getBillingRates() {
        return ResponseEntity.ok(settingsService.getBillingRates());
    }

    /** FR-21 - "configurable by Admin": write access is Admin-only, enforced again here via
     *  SecurityConfig's narrower PUT-specific rule ahead of the broader GET one. */
    @PutMapping("/billing-rates")
    public ResponseEntity<BillingRatesResponse> updateBillingRates(@Valid @RequestBody UpdateBillingRatesRequest request) {
        return ResponseEntity.ok(settingsService.updateBillingRates(request.serviceChargeRate(), request.vatRate()));
    }
}
