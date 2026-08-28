package com.rms.service;

import com.rms.domain.Customer;
import com.rms.dto.request.RegisterCustomerRequest;
import com.rms.dto.response.CustomerResponse;
import com.rms.exception.InvalidOrderStateException;
import com.rms.exception.ResourceNotFoundException;
import com.rms.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-14 - Module 2.10 CRM lookup. Deliberately separate from OrderService's own
 * find-or-create-on-submit path (OrderService still silently registers a walk-in if a
 * phone is typed but never searched) - this service backs the explicit "tap Search" /
 * "Register" UI actions Appendix B.1 describes, giving the waiter a chance to see the
 * customer's name and loyalty tier BEFORE the order is placed.
 */
@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;

    @Transactional(readOnly = true)
    public CustomerResponse lookupByPhone(String phoneNumber) {
        Customer customer = customerRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new ResourceNotFoundException("No customer found for phone " + phoneNumber));
        return CustomerResponse.from(customer);
    }

    @Transactional
    public CustomerResponse register(RegisterCustomerRequest request) {
        if (customerRepository.findByPhoneNumber(request.phoneNumber()).isPresent()) {
            throw new InvalidOrderStateException("A customer with phone " + request.phoneNumber() + " is already registered");
        }
        Customer saved = customerRepository.save(Customer.builder()
                .name(request.name())
                .phoneNumber(request.phoneNumber())
                .build());
        return CustomerResponse.from(saved);
    }
}
