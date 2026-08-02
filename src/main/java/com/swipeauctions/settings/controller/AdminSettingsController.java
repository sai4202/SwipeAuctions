package com.swipeauctions.settings.controller;

import com.swipeauctions.admin.entity.Admin;
import com.swipeauctions.admin.enums.AuditAction;
import com.swipeauctions.admin.service.AdminAuditLogService;
import com.swipeauctions.common.util.LoggedInUserUtil;
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
import java.util.Map;
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

    private final LoggedInUserUtil loggedInUserUtil;

    private final AdminAuditLogService auditLogService;

    @PutMapping("/registration-fee")
    public BigDecimal updateRegistrationFee(@Valid @RequestBody UpdateFeeRequest req) {
        Admin admin = loggedInUserUtil.getCurrentAdmin();
        BigDecimal fee = settingsService.updateRegistrationFee(req.fee());
        auditLogService.record(admin, AuditAction.REGISTRATION_FEE_UPDATED, "Settings", null,
                "Set registration fee to " + fee);
        return fee;
    }

    @PutMapping("/subscription-prices")
    public List<SettingsController.SubscriptionPriceResponse> updateSubscriptionPrices(
            @Valid @RequestBody UpdatePricesRequest req) {
        Admin admin = loggedInUserUtil.getCurrentAdmin();
        var updates = req.prices().stream()
                .map(p -> new PlatformSettingsService.PriceUpdate(p.tier(), p.billingCycle(), p.price()))
                .toList();
        var result = settingsService.updateSubscriptionPrices(updates).stream()
                .map(p -> new SettingsController.SubscriptionPriceResponse(p.getTier(), p.getBillingCycle(), p.getPrice()))
                .toList();
        auditLogService.record(admin, AuditAction.SUBSCRIPTION_PRICES_UPDATED, "Settings", null,
                "Updated subscription prices (" + updates.size() + " tier/cycle rows)");
        return result;
    }

    @PostMapping("/membership-benefits")
    public SettingsController.MembershipBenefitResponse addMembershipBenefit(
            @Valid @RequestBody CreateMembershipBenefitRequest req) {
        Admin admin = loggedInUserUtil.getCurrentAdmin();
        var benefit = membershipBenefitService.addBenefit(req.name(), req.paid(), req.prices(), req.minDeposit());
        String qualifier = benefit.isPaid() ? " (paid)" : benefit.getMinDeposit() != null ? " (min deposit " + benefit.getMinDeposit() + ")" : "";
        auditLogService.record(admin, AuditAction.MEMBERSHIP_BENEFIT_ADDED, "Settings", benefit.getId().toString(),
                "Added membership benefit \"" + benefit.getName() + "\"" + qualifier);
        return SettingsController.toResponse(benefit);
    }

    @PutMapping("/membership-benefits/tiers")
    public List<SettingsController.MembershipBenefitResponse> updateMembershipBenefitTiers(
            @Valid @RequestBody UpdateMembershipBenefitTiersRequest req) {
        Admin admin = loggedInUserUtil.getCurrentAdmin();
        var updates = req.updates().stream()
                .map(u -> new MembershipBenefitService.TierUpdate(u.benefitId(), u.enabledTiers()))
                .toList();
        var result = membershipBenefitService.updateBenefitTiers(updates).stream()
                .map(SettingsController::toResponse)
                .toList();
        auditLogService.record(admin, AuditAction.MEMBERSHIP_BENEFIT_TIERS_UPDATED, "Settings", null,
                "Updated tier assignment for " + updates.size() + " membership benefit(s)");
        return result;
    }

    @DeleteMapping("/membership-benefits/{id}")
    public void removeMembershipBenefit(@PathVariable UUID id) {
        Admin admin = loggedInUserUtil.getCurrentAdmin();
        membershipBenefitService.removeBenefit(id);
        auditLogService.record(admin, AuditAction.MEMBERSHIP_BENEFIT_REMOVED, "Settings", id.toString(),
                "Removed a membership benefit");
    }

    public record UpdateFeeRequest(@NotNull @PositiveOrZero BigDecimal fee) {}

    public record PriceEntry(@NotNull SubscriptionTier tier, @NotNull BillingCycle billingCycle,
                              @NotNull @PositiveOrZero BigDecimal price) {}

    public record UpdatePricesRequest(@NotNull List<PriceEntry> prices) {}

    public record CreateMembershipBenefitRequest(@NotBlank @Size(max = 200) String name, boolean paid,
                                                  Map<BillingCycle, BigDecimal> prices, BigDecimal minDeposit) {}

    public record BenefitTierEntry(@NotNull UUID benefitId, @NotNull Set<SubscriptionTier> enabledTiers) {}

    public record UpdateMembershipBenefitTiersRequest(@NotNull List<BenefitTierEntry> updates) {}
}
