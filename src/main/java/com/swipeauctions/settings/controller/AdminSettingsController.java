package com.swipeauctions.settings.controller;

import com.swipeauctions.enums.BillingCycle;
import com.swipeauctions.enums.SubscriptionTier;
import com.swipeauctions.settings.service.PlatformSettingsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/** Admin edits for the registration fee and the 12 subscription tier/cycle prices. Gated by the
 *  existing {@code /api/admin/**} -> hasRole(ADMIN) rule in SecurityConfig, same as every other
 *  admin controller. */
@RestController
@RequestMapping("/api/admin/settings")
@RequiredArgsConstructor
public class AdminSettingsController {

    private final PlatformSettingsService settingsService;

    @PutMapping("/registration-fee")
    public BigDecimal updateRegistrationFee(@Valid @RequestBody UpdateFeeRequest req) {
        return settingsService.updateRegistrationFee(req.fee());
    }

    @PutMapping("/subscription-prices")
    public List<SettingsController.SubscriptionPriceResponse> updateSubscriptionPrices(
            @Valid @RequestBody UpdatePricesRequest req) {
        var updates = req.prices().stream()
                .map(p -> new PlatformSettingsService.PriceUpdate(p.tier(), p.billingCycle(), p.price()))
                .toList();
        return settingsService.updateSubscriptionPrices(updates).stream()
                .map(p -> new SettingsController.SubscriptionPriceResponse(p.getTier(), p.getBillingCycle(), p.getPrice()))
                .toList();
    }

    public record UpdateFeeRequest(@NotNull @PositiveOrZero BigDecimal fee) {}

    public record PriceEntry(@NotNull SubscriptionTier tier, @NotNull BillingCycle billingCycle,
                              @NotNull @PositiveOrZero BigDecimal price) {}

    public record UpdatePricesRequest(@NotNull List<PriceEntry> prices) {}
}
