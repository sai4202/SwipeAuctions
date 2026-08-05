package com.swipeauctions.auction.scheduler;

import com.swipeauctions.auction.config.SettlementReminderConfig;
import com.swipeauctions.auction.service.AuctionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/** Periodically re-emails auction winners who still owe a settlement remainder, every
 *  {@code auction.settlement-reminder.interval-hours} until it's paid off. */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentReminderScheduler {

    private final AuctionService auctionService;
    private final SettlementReminderConfig config;

    @Scheduled(fixedDelayString = "${auction.settlement-reminder.interval-ms:3600000}")
    public void sendDueReminders() {
        if (!config.isEnabled()) {
            return;
        }

        List<UUID> due = auctionService.findAuctionsDueForSettlementReminder(config.getIntervalHours());

        // Each reminder is sent through its own call (own transaction) so one bad row can't block
        // every other winner from being reminded in the same tick — same rationale as
        // AuctionScheduler/StaleRegistrationCleanupScheduler.
        for (UUID auctionId : due) {
            try {
                auctionService.sendSettlementReminder(auctionId);
            } catch (Exception e) {
                log.warn("Failed to send settlement reminder for auction {}: {}", auctionId, e.getMessage());
            }
        }

        if (!due.isEmpty()) {
            log.info("Settlement reminders: sent {} reminder(s) for unpaid auction settlements", due.size());
        }
    }
}
