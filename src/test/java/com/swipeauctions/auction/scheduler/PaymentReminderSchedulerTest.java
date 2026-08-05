package com.swipeauctions.auction.scheduler;

import com.swipeauctions.auction.config.SettlementReminderConfig;
import com.swipeauctions.auction.service.AuctionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The reminder tick: respects the enabled kill-switch and isolates one bad row from the rest —
 *  same shape as StaleRegistrationCleanupSchedulerTest. */
@ExtendWith(MockitoExtension.class)
class PaymentReminderSchedulerTest {

    @Mock private AuctionService auctionService;
    @Mock private SettlementReminderConfig config;

    private PaymentReminderScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new PaymentReminderScheduler(auctionService, config);
    }

    @Test
    void disabled_skipsEntirely() {
        when(config.isEnabled()).thenReturn(false);

        scheduler.sendDueReminders();

        verify(auctionService, never()).findAuctionsDueForSettlementReminder(anyLong());
    }

    @Test
    void oneFailingAuction_doesNotStopTheRestFromBeingReminded() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        when(config.isEnabled()).thenReturn(true);
        when(config.getIntervalHours()).thenReturn(24L);
        when(auctionService.findAuctionsDueForSettlementReminder(24L)).thenReturn(List.of(first, second));
        org.mockito.Mockito.doThrow(new RuntimeException("simulated failure"))
                .when(auctionService).sendSettlementReminder(eq(first));

        scheduler.sendDueReminders();

        verify(auctionService).sendSettlementReminder(first);
        verify(auctionService).sendSettlementReminder(second);
    }
}
