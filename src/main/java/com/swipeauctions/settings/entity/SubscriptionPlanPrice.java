package com.swipeauctions.settings.entity;

import com.swipeauctions.common.entity.BaseEntity;
import com.swipeauctions.enums.BillingCycle;
import com.swipeauctions.enums.SubscriptionTier;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/** Admin-set price for one (tier, billingCycle) combination. Exactly 12 rows (3 tiers x 4 cycles),
 *  seeded by V16 — admins edit prices, they never add/remove rows. */
@Entity
@Table(name = "subscription_plan_prices", uniqueConstraints = {
        @UniqueConstraint(name = "uk_subscription_plan_prices_tier_cycle", columnNames = {"tier", "billing_cycle"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionPlanPrice extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubscriptionTier tier;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_cycle", nullable = false, length = 20)
    private BillingCycle billingCycle;

    @Builder.Default
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price = BigDecimal.ZERO;
}
