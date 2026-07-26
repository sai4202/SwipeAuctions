package com.swipeauctions.auction.scheduler;

import com.swipeauctions.auction.service.AuctionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Periodically opens auctions whose start time has passed and closes those past their end time. */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuctionScheduler {

    private final AuctionService auctionService;

    @Scheduled(fixedDelayString = "${auction.scheduler.interval-ms:5000}")
    public void tick() {
        auctionService.openDueAuctions();
        // Each auction is closed through the auctionService proxy in its own call (own transaction)
        // so one auction that fails to settle (e.g. a data issue on that one row) can't roll back the
        // close of every other due auction in the same tick.
        for (UUID auctionId : auctionService.findAuctionsDueForClose()) {
            try {
                auctionService.closeAuctionById(auctionId);
            } catch (Exception e) {
                log.warn("Failed to close auction {}: {}", auctionId, e.getMessage());
            }
        }
        auctionService.releaseDueProceeds();
    }
}
