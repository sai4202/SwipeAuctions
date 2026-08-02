package com.swipeauctions.settings.entity;

import com.swipeauctions.common.entity.BaseEntity;
import com.swipeauctions.enums.BillingCycle;
import com.swipeauctions.enums.SubscriptionTier;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Admin-managed perk shown under every membership tier card. {@code enabledTiers} is the set of
 *  tiers this benefit applies to — admin toggles membership per tier via a checkbox grid; a tier
 *  not in the set renders as a red cross on that tier's card instead of a green check. {@code paid}
 *  marks a benefit as requiring extra payment on top of the tier subscription itself, priced per
 *  billing cycle in {@code prices} (all four cycles required when paid — see
 *  MembershipBenefitService#addBenefit). {@code minDeposit}, mutually exclusive with {@code paid},
 *  instead gates the benefit behind a minimum wallet deposit rather than a separate charge — e.g.
 *  "Pan India Access" unlocked once the buyer has deposited at least that much. Purely informational
 *  (shown on the membership page) — not enforced anywhere else yet. */
@Entity
@Table(name = "membership_benefits")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MembershipBenefit extends BaseEntity {

    @Column(nullable = false, length = 200)
    private String name;

    @Builder.Default
    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "membership_benefit_tiers", joinColumns = @JoinColumn(name = "benefit_id"))
    @Column(name = "tier", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private Set<SubscriptionTier> enabledTiers = new HashSet<>();

    @Builder.Default
    @Column(nullable = false)
    private boolean paid = false;

    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "membership_benefit_prices", joinColumns = @JoinColumn(name = "benefit_id"))
    @MapKeyColumn(name = "billing_cycle")
    @MapKeyEnumerated(EnumType.STRING)
    @Column(name = "price", nullable = false, precision = 12, scale = 2)
    private Map<BillingCycle, BigDecimal> prices = new HashMap<>();

    @Column(name = "min_deposit", precision = 12, scale = 2)
    private BigDecimal minDeposit;
}
