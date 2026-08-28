package com.rms.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Module 2.8 - one row per cashier shift, closed out with a system-vs-drawer variance. */
@Entity
@Table(name = "shifts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Shift {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cashier_id", nullable = false)
    private User cashier;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "system_cash_total", precision = 12, scale = 2)
    private BigDecimal systemCashTotal;

    @Column(name = "system_card_total", precision = 12, scale = 2)
    private BigDecimal systemCardTotal;

    @Column(name = "system_digital_total", precision = 12, scale = 2)
    private BigDecimal systemDigitalTotal;

    @Column(name = "declared_drawer_amount", precision = 12, scale = 2)
    private BigDecimal declaredDrawerAmount;

    /** declaredDrawerAmount - systemCashTotal, persisted so Manager review does not recompute it. */
    @Column(name = "variance", precision = 12, scale = 2)
    private BigDecimal variance;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_manager_id")
    private User reviewedBy;
}
