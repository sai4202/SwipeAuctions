package com.swipeauctions.auction.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * A winner who can't fully pay a settlement at auction close (see
 * {@code AuctionService#completeSettlement}) can otherwise leave the balance unpaid indefinitely.
 * This backs {@code PaymentReminderScheduler}, which re-emails them every {@code interval-hours}
 * until it's paid. `enabled` is a kill-switch to pause reminders without a redeploy.
 */
@Component
public class SettlementReminderConfig {

    @Value("${auction.settlement-reminder.enabled:true}")
    private boolean enabled;

    @Value("${auction.settlement-reminder.interval-hours:24}")
    private long intervalHours;

    public boolean isEnabled() {
        return enabled;
    }

    public long getIntervalHours() {
        return intervalHours;
    }
}
