package com.rms.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Module 2.9 - non-sales stock reductions: spoilage, breakage, expiry, calibration waste. */
@Entity
@Table(name = "waste_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WasteLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ingredient_id", nullable = false)
    private Ingredient ingredient;

    @Column(name = "quantity_wasted", nullable = false, precision = 12, scale = 3)
    private BigDecimal quantityWasted;

    @Column(length = 30, nullable = false)
    private String reasonCode; // SPOILAGE, BREAKAGE, EXPIRY, CALIBRATION

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "logged_by_user_id", nullable = false)
    private User loggedBy;

    @Column(name = "logged_at", updatable = false)
    private LocalDateTime loggedAt;

    @PrePersist
    void onCreate() {
        this.loggedAt = LocalDateTime.now();
    }
}
