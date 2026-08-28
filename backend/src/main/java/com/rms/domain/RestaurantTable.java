package com.rms.domain;

import com.rms.domain.enums.TableStatus;
import jakarta.persistence.*;
import lombok.*;

/**
 * Named RestaurantTable (not Table) to avoid colliding with javax/jakarta reserved words
 * and the java.sql.Time family of imports elsewhere in the codebase.
 */
@Entity
@Table(name = "restaurant_tables")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RestaurantTable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "table_number", nullable = false, unique = true, length = 10)
    private String tableNumber;

    @Column(name = "seating_capacity", nullable = false)
    private Integer seatingCapacity;

    @Enumerated(EnumType.STRING)
    @Column(name = "operational_status", nullable = false, length = 20)
    @Builder.Default
    private TableStatus operationalStatus = TableStatus.AVAILABLE;

    /**
     * Nullable pointer to the order currently bound to this table. Kept as a plain FK
     * column (not a bidirectional @OneToOne) so releasing a table is a one-column update,
     * not a full entity graph traversal - this runs on every checkout.
     */
    @Column(name = "current_order_id")
    private Long currentOrderId;

    @Version
    private Long version;
}
