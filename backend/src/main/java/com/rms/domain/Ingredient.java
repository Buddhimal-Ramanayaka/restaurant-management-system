package com.rms.domain;

import com.rms.domain.enums.UnitType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "ingredients")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ingredient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    /**
     * The mutable, hot-path field. Every read of this column for the purpose of
     * mutating it MUST go through IngredientRepository#findByIdForUpdate, which
     * applies PESSIMISTIC_WRITE, never through a plain findById.
     */
    @Column(name = "current_stock", nullable = false, precision = 12, scale = 3)
    private BigDecimal currentStock;

    @Column(name = "reorder_level", nullable = false, precision = 12, scale = 3)
    private BigDecimal reorderLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit_type", nullable = false, length = 10)
    private UnitType unitType;

    /** Weighted-average unit cost, recomputed by GrnService on each goods receipt (Module 2.7). */
    @Column(name = "average_unit_cost", precision = 12, scale = 4)
    @Builder.Default
    private BigDecimal averageUnitCost = BigDecimal.ZERO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "preferred_supplier_id")
    private Supplier preferredSupplier;

    /**
     * JPA @Version gives us optimistic locking for free on plain saves elsewhere in the
     * app (e.g. editing reorder_level from the Admin screen). The concurrency guarantee
     * that matters for the deduction engine is the explicit pessimistic read in the
     * repository, not this column - the two mechanisms defend different code paths.
     */
    @Version
    private Long version;
}
