package com.swipeauctions.settings.controller;

import com.swipeauctions.enums.BillingCycle;
import com.swipeauctions.enums.SubscriptionTier;
import com.swipeauctions.settings.service.MembershipBenefitService;
import com.swipeauctions.settings.service.PlatformSettingsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Admin edits for the registration fee, the 12 subscription tier/cycle prices, and the membership
 *  benefit list. Gated by the existing {@code /api/admin/**} -> hasRole(ADMIN) rule in
 *  SecurityConfig, same as every other admin controller. */
@RestController
@RequestMapping("/api/admin/settings")
@RequiredArgsConstructor
public class AdminSettingsController {

    private final PlatformSettingsService settingsService;

    private final MembershipBenefitService membershipBenefitService;

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

    @PostMapping("/membership-benefits")
    public SettingsController.MembershipBenefitResponse addMembershipBenefit(
            @Valid @RequestBody CreateMembershipBenefitRequest req) {
        return SettingsController.toResponse(membershipBenefitService.addBenefit(req.name()));
    }

    @PutMapping("/membership-benefits/tiers")
    public List<SettingsController.MembershipBenefitResponse> updateMembershipBenefitTiers(
            @Valid @RequestBody UpdateMembershipBenefitTiersRequest req) {
        var updates = req.updates().stream()
                .map(u -> new MembershipBenefitService.TierUpdate(u.benefitId(), u.enabledTiers()))
                .toList();
        return membershipBenefitService.updateBenefitTiers(updates).stream()
                .map(SettingsController::toResponse)
                .toList();
    }

    @DeleteMapping("/membership-benefits/{id}")
    public void removeMembershipBenefit(@PathVariable UUID id) {
        membershipBenefitService.removeBenefit(id);
    }

    public record UpdateFeeRequest(@NotNull @PositiveOrZero BigDecimal fee) {}

    public record PriceEntry(@NotNull SubscriptionTier tier, @NotNull BillingCycle billingCycle,
                              @NotNull @PositiveOrZero BigDecimal price) {}

    public record UpdatePricesRequest(@NotNull List<PriceEntry> prices) {}

    public record CreateMembershipBenefitRequest(@NotBlank @Size(max = 200) String name) {}

    public record BenefitTierEntry(@NotNull UUID benefitId, @NotNull Set<SubscriptionTier> enabledTiers) {}

    public record UpdateMembershipBenefitTiersRequest(@NotNull List<BenefitTierEntry> updates) {}
}
