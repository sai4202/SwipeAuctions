package com.swipeauctions.settings.controller;

import com.swipeauctions.common.util.LoggedInUserUtil;
import com.swipeauctions.enums.BillingCycle;
import com.swipeauctions.enums.SubscriptionTier;
import com.swipeauctions.settings.service.SubscriptionService;
import com.swipeauctions.user.entity.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** Self-service subscribe (dev stub, no payment — see SubscriptionService javadoc). */
@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final LoggedInUserUtil loggedInUserUtil;

    @GetMapping("/me")
    public SubscriptionResponse me() {
        User user = loggedInUserUtil.getCurrentUser();
        return new SubscriptionResponse(subscriptionService.currentTier(user), user.getSubscriptionExpiresAt());
    }

    @PostMapping("/subscribe")
    public SubscriptionResponse subscribe(@Valid @RequestBody SubscribeRequest req) {
        User user = loggedInUserUtil.getCurrentUser();
        User updated = subscriptionService.subscribe(user, req.tier(), req.billingCycle());
        return new SubscriptionResponse(updated.getSubscriptionTier(), updated.getSubscriptionExpiresAt());
    }

    public record SubscribeRequest(@NotNull SubscriptionTier tier, @NotNull BillingCycle billingCycle) {}

    public record SubscriptionResponse(SubscriptionTier tier, java.time.LocalDateTime expiresAt) {}
}
