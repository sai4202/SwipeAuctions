package com.swipeauctions.settings.service;

import com.swipeauctions.enums.BillingCycle;
import com.swipeauctions.enums.SubscriptionTier;
import com.swipeauctions.settings.entity.PlatformSettings;
import com.swipeauctions.settings.entity.SubscriptionPlanPrice;
import com.swipeauctions.settings.repository.PlatformSettingsRepository;
import com.swipeauctions.settings.repository.SubscriptionPlanPriceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Admin-configurable platform fees: the one-time registration fee, and the 12 (tier x cycle)
 * subscription prices. {@code RazorpayPaymentService} reads these when creating a registration-fee
 * or subscription order, so a value changed here takes effect on the next order a user starts.
 */
@Service
@RequiredArgsConstructor
public class PlatformSettingsService {

    private final PlatformSettingsRepository settingsRepository;
    private final SubscriptionPlanPriceRepository priceRepository;

    @Transactional
    public PlatformSettings getOrCreateSettings() {
        List<PlatformSettings> all = settingsRepository.findAll();
        return all.isEmpty() ? settingsRepository.save(new PlatformSettings()) : all.get(0);
    }

    @Transactional(readOnly = true)
    public BigDecimal getRegistrationFee() {
        return getOrCreateSettings().getRegistrationFee();
    }

    @Transactional
    public BigDecimal updateRegistrationFee(BigDecimal fee) {
        PlatformSettings settings = getOrCreateSettings();
        settings.setRegistrationFee(fee);
        return settingsRepository.save(settings).getRegistrationFee();
    }

    @Transactional(readOnly = true)
    public List<SubscriptionPlanPrice> listSubscriptionPrices() {
        return priceRepository.findAll();
    }

    @Transactional
    public List<SubscriptionPlanPrice> updateSubscriptionPrices(List<PriceUpdate> updates) {
        for (PriceUpdate update : updates) {
            SubscriptionPlanPrice row = priceRepository.findByTierAndBillingCycle(update.tier(), update.billingCycle())
                    .orElseGet(() -> SubscriptionPlanPrice.builder().tier(update.tier()).billingCycle(update.billingCycle()).build());
            row.setPrice(update.price());
            priceRepository.save(row);
        }
        return priceRepository.findAll();
    }

    public record PriceUpdate(SubscriptionTier tier, BillingCycle billingCycle, BigDecimal price) {}
}
