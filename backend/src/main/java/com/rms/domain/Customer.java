package com.rms.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/** Module 2.10 - lightweight CRM record, looked up by phone number from the POS. */
@Entity
@Table(name = "customers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "phone_number", nullable = false, unique = true, length = 15)
    private String phoneNumber;

    @Column(name = "visit_count", nullable = false)
    @Builder.Default
    private Integer visitCount = 0;

    @Column(name = "lifetime_spend", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal lifetimeSpend = BigDecimal.ZERO;

    @Column(name = "loyalty_tier", length = 20)
    @Builder.Default
    private String loyaltyTier = "STANDARD";
}
