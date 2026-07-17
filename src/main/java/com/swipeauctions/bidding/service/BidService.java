package com.swipeauctions.bidding.service;

import com.swipeauctions.auction.entity.Auction;
import com.swipeauctions.auction.enums.AuctionStatus;
import com.swipeauctions.auction.repository.AuctionRepository;
import com.swipeauctions.bidding.entity.Bid;
import com.swipeauctions.bidding.repository.BidRepository;
import com.swipeauctions.common.exception.BadRequestException;
import com.swipeauctions.common.exception.ResourceNotFoundException;
import com.swipeauctions.user.entity.User;
import com.swipeauctions.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Core bid placement. One transaction: pessimistic-lock the auction, validate, check the wallet
 * deposit hold (the bidding gate), insert the bid, update the highest bid, apply anti-snipe, and
 * broadcast to STOMP subscribers only AFTER_COMMIT.
 */
@Service
@RequiredArgsConstructor
public class BidService {

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final WalletService walletService;
    private final SimpMessagingTemplate messagingTemplate;

    @Value("${auction.anti-snipe.window-seconds:30}")
    private long antiSnipeWindowSeconds;

    @Value("${auction.anti-snipe.extension-seconds:30}")
    private long antiSnipeExtensionSeconds;

    @Value("${auction.anti-snipe.max-extensions:10}")
    private int maxExtensions;

    @Value("${auction.min-increment:1}")
    private BigDecimal minIncrement;

    @Transactional
    public Bid placeBid(UUID auctionId, User bidder, BigDecimal amount) {
        Auction auction = auctionRepository.findByIdForUpdate(auctionId)
                .orElseThrow(() -> new ResourceNotFoundException("Auction not found"));

        if (auction.getStatus() != AuctionStatus.OPEN) {
            throw new BadRequestException("Auction is not open for bidding");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(auction.getCurrentEndTime())) {
            throw new BadRequestException("Auction has already ended");
        }
        if (auction.getListing().getSeller().getId().equals(bidder.getId())) {
            throw new BadRequestException("Sellers cannot bid on their own auction");
        }
        if (!walletService.hasActiveHold(auctionId, bidder.getId())) {
            throw new BadRequestException("Register to bid first — a refundable deposit hold is required");
        }

        BigDecimal minAllowed = auction.getCurrentHighestBid() != null
                ? auction.getCurrentHighestBid().add(minIncrement)
                : auction.getBasePrice();
        if (amount.compareTo(minAllowed) < 0) {
            throw new BadRequestException("Bid must be at least " + minAllowed);
        }

        Bid bid = bidRepository.save(Bid.builder()
                .auction(auction).bidder(bidder).amount(amount).placedAt(now).build());

        auction.setCurrentHighestBid(amount);
        auction.setCurrentWinner(bidder);

        // Anti-snipe: a late bid extends the deadline, up to a cap.
        long remaining = Duration.between(now, auction.getCurrentEndTime()).getSeconds();
        if (remaining <= antiSnipeWindowSeconds && auction.getExtensionCount() < maxExtensions) {
            auction.setCurrentEndTime(auction.getCurrentEndTime().plusSeconds(antiSnipeExtensionSeconds));
            auction.setExtensionCount(auction.getExtensionCount() + 1);
        }
        auctionRepository.save(auction);

        broadcastAfterCommit(auctionId, amount, auction.getCurrentEndTime(),
                bidRepository.countByAuction_Id(auctionId) + 1);
        return bid;
    }

    private void broadcastAfterCommit(UUID auctionId, BigDecimal amount, LocalDateTime endTime, long bidCount) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("auctionId", auctionId.toString());
        payload.put("currentHighestBid", amount);
        payload.put("currentEndTime", endTime.toString());
        payload.put("bidCount", bidCount);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    messagingTemplate.convertAndSend("/topic/auctions/" + auctionId, (Object) payload);
                }
            });
        } else {
            messagingTemplate.convertAndSend("/topic/auctions/" + auctionId, (Object) payload);
        }
    }
}
