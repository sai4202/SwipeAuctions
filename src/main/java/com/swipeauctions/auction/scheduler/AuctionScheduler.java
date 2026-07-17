package com.swipeauctions.auction.scheduler;

import com.swipeauctions.auction.service.AuctionService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodically opens auctions whose start time has passed and closes those past their end time. */
@Component
@RequiredArgsConstructor
public class AuctionScheduler {

    private final AuctionService auctionService;

    @Scheduled(fixedDelayString = "${auction.scheduler.interval-ms:5000}")
    public void tick() {
        auctionService.openDueAuctions();
        auctionService.closeDueAuctions();
    }
}
