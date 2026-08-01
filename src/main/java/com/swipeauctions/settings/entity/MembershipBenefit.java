package com.swipeauctions.settings.entity;

import com.swipeauctions.common.entity.BaseEntity;
import com.swipeauctions.enums.SubscriptionTier;
import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

/** Admin-managed perk shown under every membership tier card. {@code enabledTiers} is the set of
 *  tiers this benefit applies to — admin toggles membership per tier via a checkbox grid; a tier
 *  not in the set renders as a red cross on that tier's card instead of a green check. */
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
}
