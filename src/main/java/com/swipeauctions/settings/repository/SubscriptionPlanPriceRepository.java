package com.swipeauctions.settings.repository;

import com.swipeauctions.enums.BillingCycle;
import com.swipeauctions.enums.SubscriptionTier;
import com.swipeauctions.settings.entity.SubscriptionPlanPrice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SubscriptionPlanPriceRepository extends JpaRepository<SubscriptionPlanPrice, UUID> {
    Optional<SubscriptionPlanPrice> findByTierAndBillingCycle(SubscriptionTier tier, BillingCycle billingCycle);
}
