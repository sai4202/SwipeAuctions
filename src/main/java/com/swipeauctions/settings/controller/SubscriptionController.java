package com.swipeauctions.settings.controller;

import com.swipeauctions.common.util.LoggedInUserUtil;
import com.swipeauctions.enums.BillingCycle;
import com.swipeauctions.enums.SubscriptionTier;
import com.swipeauctions.payment.RazorpayPaymentService;
import com.swipeauctions.settings.service.SubscriptionService;
import com.swipeauctions.user.entity.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.UUID;

/** Real Razorpay payment for a subscription tier — order then verify, same pattern as wallet top-up. */
@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final RazorpayPaymentService razorpayPaymentService;
    private final LoggedInUserUtil loggedInUserUtil;

    @GetMapping("/me")
    public SubscriptionResponse me() {
        User user = loggedInUserUtil.getCurrentUser();
        return new SubscriptionResponse(subscriptionService.currentTier(user), user.getSubscriptionExpiresAt());
    }

    @PostMapping("/order")
    public RazorpayPaymentService.OrderIntent createOrder(@Valid @RequestBody SubscribeRequest req) {
        User user = loggedInUserUtil.getCurrentUser();
        return razorpayPaymentService.createSubscriptionOrder(user, req.tier(), req.billingCycle(), req.addonBenefitIds());
    }

    @PostMapping("/verify")
    public SubscriptionResponse verify(@Valid @RequestBody VerifyPaymentRequest req) {
        User user = loggedInUserUtil.getCurrentUser();
        razorpayPaymentService.verifyAndSettle(user, req.orderId(), req.paymentId(), req.signature());
        User updated = loggedInUserUtil.getCurrentUser();
        return new SubscriptionResponse(updated.getSubscriptionTier(), updated.getSubscriptionExpiresAt());
    }

    public record SubscribeRequest(@NotNull SubscriptionTier tier, @NotNull BillingCycle billingCycle,
                                    Set<UUID> addonBenefitIds) {}

    public record VerifyPaymentRequest(
            @NotBlank String orderId, @NotBlank String paymentId, @NotBlank String signature) {}

    public record SubscriptionResponse(SubscriptionTier tier, java.time.LocalDateTime expiresAt) {}
}
