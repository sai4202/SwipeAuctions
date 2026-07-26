package com.swipeauctions.settings.service;

import com.swipeauctions.enums.BillingCycle;
import com.swipeauctions.enums.SubscriptionTier;
import com.swipeauctions.user.entity.User;
import com.swipeauctions.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Grants a subscription tier to a user. No payment is collected here — no wallet debit, no Stripe
 * call — this only updates tier/expiry state so the tier-gating logic (see AuctionController's
 * register/bid checks) can be exercised end-to-end before real billing exists. Same "stub until
 * Phase 2" shape as {@link com.swipeauctions.wallet.service.WalletService#topUp}.
 */
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final UserRepository userRepository;

    @Transactional
    public User subscribe(User user, SubscriptionTier tier, BillingCycle billingCycle) {
        LocalDateTime now = LocalDateTime.now();
        user.setSubscriptionTier(tier);
        user.setSubscriptionExpiresAt(billingCycle.expiryFrom(now));
        return userRepository.save(user);
    }

    /** The user's tier, or NONE if they never subscribed or their subscription has lapsed. */
    public SubscriptionTier currentTier(User user) {
        if (user.getSubscriptionTier() == null || user.getSubscriptionTier() == SubscriptionTier.NONE) {
            return SubscriptionTier.NONE;
        }
        if (user.getSubscriptionExpiresAt() != null && user.getSubscriptionExpiresAt().isBefore(LocalDateTime.now())) {
            return SubscriptionTier.NONE;
        }
        return user.getSubscriptionTier();
    }
}
