package com.swipeauctions.settings.controller;

import com.swipeauctions.enums.BillingCycle;
import com.swipeauctions.enums.SubscriptionTier;
import com.swipeauctions.settings.service.PlatformSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/** Public, read-only settings — the registration fee display on signup and the subscription
 *  price grid both need these without requiring a login. */
@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final PlatformSettingsService settingsService;

    @GetMapping("/registration-fee")
    public BigDecimal registrationFee() {
        return settingsService.getRegistrationFee();
    }

    @GetMapping("/subscription-prices")
    public List<SubscriptionPriceResponse> subscriptionPrices() {
        return settingsService.listSubscriptionPrices().stream()
                .map(p -> new SubscriptionPriceResponse(p.getTier(), p.getBillingCycle(), p.getPrice()))
                .toList();
    }

    public record SubscriptionPriceResponse(SubscriptionTier tier, BillingCycle billingCycle, BigDecimal price) {}
}
