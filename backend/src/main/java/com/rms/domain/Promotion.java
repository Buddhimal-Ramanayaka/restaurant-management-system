package com.rms.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalTime;

/** Module 2.10 - rule-based discount config evaluated by BillingService before totals. */
@Entity
@Table(name = "promotions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Promotion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "applies_to_category", length = 50)
    private String appliesToCategory; // null = whole-bill promotion

    @Column(name = "discount_percent", precision = 5, scale = 2)
    private BigDecimal discountPercent;

    @Column(name = "buy_x_get_y_free")
    private Boolean buyXGetYFree;

    @Column(name = "active_from")
    private LocalTime activeFrom; // Happy Hour window start

    @Column(name = "active_to")
    private LocalTime activeTo;

    @Column(name = "required_loyalty_tier", length = 20)
    private String requiredLoyaltyTier;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;
}
