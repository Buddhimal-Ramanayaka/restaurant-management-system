package com.rms.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "order_details")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_order_detail_order"))
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "menu_item_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_order_detail_menu_item"))
    private MenuItem menuItem;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "special_notes", columnDefinition = "TEXT")
    private String specialNotes;

    /**
     * Per-line kitchen prep state, distinct from the parent Order.status. The Kanban
     * board on the Kitchen display moves individual line items across lanes; the parent
     * order only flips to READY once every line here is READY (see OrderService).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "line_status", nullable = false, length = 20)
    @Builder.Default
    private com.rms.domain.enums.OrderStatus lineStatus = com.rms.domain.enums.OrderStatus.PENDING;
}
