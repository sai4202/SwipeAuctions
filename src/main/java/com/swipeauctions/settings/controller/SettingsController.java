package com.swipeauctions.settings.controller;

import com.swipeauctions.auth.config.MobileVerificationConfig;
import com.swipeauctions.enums.BillingCycle;
import com.swipeauctions.enums.SubscriptionTier;
import com.swipeauctions.settings.entity.MembershipBenefit;
import com.swipeauctions.settings.service.MembershipBenefitService;
import com.swipeauctions.settings.service.PlatformSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Public, read-only settings — the registration fee display on signup and the subscription
 *  price grid both need these without requiring a login. */
@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final PlatformSettingsService settingsService;

    private final MobileVerificationConfig mobileVerificationConfig;

    private final MembershipBenefitService membershipBenefitService;

    @GetMapping("/registration-fee")
    public BigDecimal registrationFee() {
        return settingsService.getRegistrationFee();
    }

    // Lets the registration wizard know whether to render the mobile-OTP step, without hardcoding
    // that decision on the frontend — flipping AUTH_MOBILE_VERIFICATION_REQUIRED alone is then
    // enough to change both sides' behavior.
    @GetMapping("/mobile-verification-required")
    public boolean mobileVerificationRequired() {
        return mobileVerificationConfig.isRequired();
    }

    @GetMapping("/subscription-prices")
    public List<SubscriptionPriceResponse> subscriptionPrices() {
        return settingsService.listSubscriptionPrices().stream()
                .map(p -> new SubscriptionPriceResponse(p.getTier(), p.getBillingCycle(), p.getPrice()))
                .toList();
    }

    public record SubscriptionPriceResponse(SubscriptionTier tier, BillingCycle billingCycle, BigDecimal price) {}

    @GetMapping("/membership-benefits")
    public List<MembershipBenefitResponse> membershipBenefits() {
        return membershipBenefitService.listBenefits().stream()
                .map(SettingsController::toResponse)
                .toList();
    }

    static MembershipBenefitResponse toResponse(MembershipBenefit b) {
        return new MembershipBenefitResponse(b.getId(), b.getName(), b.getSortOrder(), b.getEnabledTiers());
    }

    public record MembershipBenefitResponse(UUID id, String name, int sortOrder, Set<SubscriptionTier> enabledTiers) {}
}
