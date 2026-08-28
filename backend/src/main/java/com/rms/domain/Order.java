package com.rms.domain;

import com.rms.domain.enums.OrderStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "table_id", nullable = false)
    private Long tableId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "waiter_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_order_waiter"))
    private User waiter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private OrderStatus status = OrderStatus.PENDING;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderDetail> items = new ArrayList<>();

    /** Optional CRM linkage (Module 2.10) - nullable, a walk-in need not be identified. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Set the instant the kitchen accepts the ticket (PENDING -> PREPARING). The Kitchen
     * Kanban UI computes the ">15 minutes -> highlight red" rule off this timestamp, per
     * Module 2.4, rather than off createdAt, so a ticket that sat unclaimed does not
     * silently reset its clock when a cook finally taps "start".
     */
    @Column(name = "preparing_started_at")
    private LocalDateTime preparingStartedAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void addItem(OrderDetail detail) {
        items.add(detail);
        detail.setOrder(this);
    }
}
