package com.swipeauctions.payment;

import com.swipeauctions.common.util.LoggedInUserUtil;
import com.swipeauctions.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** Seller/dealer Stripe Connect Express onboarding — required before {@code /api/wallet/withdraw}. */
@RestController
@RequestMapping("/api/seller/stripe")
@RequiredArgsConstructor
public class SellerPaymentsController {

    private final StripePaymentService stripePaymentService;
    private final LoggedInUserUtil loggedInUserUtil;

    @PostMapping("/onboarding-link")
    public OnboardingLinkResponse onboardingLink() {
        User seller = loggedInUserUtil.getCurrentUser();
        return new OnboardingLinkResponse(stripePaymentService.createOnboardingLink(seller));
    }

    @GetMapping("/status")
    public StatusResponse status() {
        User seller = loggedInUserUtil.getCurrentUser();
        return toStatus(seller);
    }

    /** Call after the user is redirected back from Stripe's onboarding flow. */
    @PostMapping("/refresh")
    public StatusResponse refresh() {
        User seller = loggedInUserUtil.getCurrentUser();
        return toStatus(stripePaymentService.refreshConnectStatus(seller));
    }

    private StatusResponse toStatus(User seller) {
        return new StatusResponse(seller.getStripeAccountId() != null,
                Boolean.TRUE.equals(seller.getStripeChargesEnabled()),
                Boolean.TRUE.equals(seller.getStripePayoutsEnabled()));
    }

    public record OnboardingLinkResponse(String url) {}

    public record StatusResponse(boolean connected, boolean chargesEnabled, boolean payoutsEnabled) {}
}
