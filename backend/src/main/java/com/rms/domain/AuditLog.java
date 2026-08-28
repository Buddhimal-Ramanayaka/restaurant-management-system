package com.rms.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Module 2.8 - populated by the @Aspect interceptor, never written to directly by services. */
@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "action_type", nullable = false, length = 100)
    private String actionType;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @Column(name = "occurred_at", updatable = false)
    private LocalDateTime occurredAt;

    @PrePersist
    void onCreate() {
        this.occurredAt = LocalDateTime.now();
    }
}
