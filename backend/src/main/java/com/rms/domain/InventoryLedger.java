package com.rms.domain;

import com.rms.domain.enums.LedgerReason;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Append-only audit trail (Module 2.7 / 2.9). Every stock mutation - a sale deduction, a
 * GRN receipt, a manual correction, or a waste write-off - inserts exactly one row here.
 * Rows are never updated or deleted; that immutability is what makes the later variance
 * report (Theoretical vs Physical stock) trustworthy.
 */
@Entity
@Table(name = "inventory_ledger")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ingredient_id", nullable = false)
    private Ingredient ingredient;

    /** Signed: negative for deductions/waste, positive for GRN receipts and upward corrections. */
    @Column(name = "quantity_delta", nullable = false, precision = 12, scale = 3)
    private BigDecimal quantityDelta;

    @Column(name = "resulting_stock", nullable = false, precision = 12, scale = 3)
    private BigDecimal resultingStock;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LedgerReason reason;

    /** Nullable pointer to whatever triggered this row (order id, GRN id, waste log id). */
    @Column(name = "reference_id")
    private Long referenceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorded_by_user_id")
    private User recordedBy;

    @Column(name = "recorded_at", updatable = false)
    private LocalDateTime recordedAt;

    @PrePersist
    void onCreate() {
        this.recordedAt = LocalDateTime.now();
    }
}
