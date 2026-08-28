package com.rms.dto.response;

/** Point-to-point payload sent to /user/{waiterUsername}/queue/order-ready. */
public record OrderReadyNotification(
        Long orderId,
        String tableNumber
) {}
