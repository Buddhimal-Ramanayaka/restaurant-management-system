package com.rms.controller;

import com.rms.dto.request.RegisterCustomerRequest;
import com.rms.dto.response.CustomerResponse;
import com.rms.service.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    /** FR-14 - the POS "tap Search" action. 404 (not 200 + null) when no match, so the
     *  waiter's UI can distinguish "not found yet" from "found, no loyalty tier". */
    @GetMapping("/lookup")
    public ResponseEntity<CustomerResponse> lookup(@RequestParam String phone) {
        return ResponseEntity.ok(customerService.lookupByPhone(phone));
    }

    @PostMapping
    public ResponseEntity<CustomerResponse> register(@Valid @RequestBody RegisterCustomerRequest request) {
        return ResponseEntity.ok(customerService.register(request));
    }
}
