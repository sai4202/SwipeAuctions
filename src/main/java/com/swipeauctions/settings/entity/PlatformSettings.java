package com.swipeauctions.settings.entity;

import com.swipeauctions.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/** Singleton row of platform-wide, admin-editable settings. Only one row ever exists. */
@Entity
@Table(name = "platform_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlatformSettings extends BaseEntity {

    @Builder.Default
    @Column(name = "registration_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal registrationFee = BigDecimal.ZERO;
}
