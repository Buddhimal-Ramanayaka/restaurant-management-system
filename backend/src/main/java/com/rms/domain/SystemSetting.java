package com.rms.domain;

import jakarta.persistence.*;
import lombok.*;

/** FR-21 - key-value store for Admin-configurable values (currently just the two billing rates). */
@Entity
@Table(name = "system_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "setting_key", nullable = false, unique = true, length = 50)
    private String settingKey;

    @Column(name = "setting_value", nullable = false, length = 50)
    private String settingValue;
}
