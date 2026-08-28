package com.rms.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Module 2.11 - advance table bookings feeding the RESERVED table-status flag. */
@Entity
@Table(name = "reservations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_name", nullable = false, length = 100)
    private String customerName;

    @Column(name = "customer_phone", nullable = false, length = 15)
    private String customerPhone;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "table_id", nullable = false)
    private RestaurantTable table;

    @Column(name = "reservation_time", nullable = false)
    private LocalDateTime reservationTime;

    @Column(name = "party_size", nullable = false)
    private Integer partySize;

    @Column(length = 20, nullable = false)
    @Builder.Default
    private String status = "BOOKED"; // BOOKED, CHECKED_IN, NO_SHOW, CANCELLED
}
