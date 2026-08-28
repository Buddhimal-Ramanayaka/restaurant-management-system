package com.rms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the Restaurant Management System backend.
 *
 * EnableAsync      -> lets InventoryAlertService push WebSocket alerts without blocking the
 *                     request thread that triggered the stock deduction.
 * EnableScheduling -> reserved for the reservation-window checker (Module 2.11) and shift
 *                     auto-close jobs (Module 2.8).
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class RmsApplication {
    public static void main(String[] args) {
        SpringApplication.run(RmsApplication.class, args);
    }
}
