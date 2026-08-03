package com.swipeauctions.bidding.service;

import com.swipeauctions.admin.entity.Admin;
import com.swipeauctions.auction.entity.Auction;
import com.swipeauctions.auction.enums.AuctionStatus;
import com.swipeauctions.auction.repository.AuctionRepository;
import com.swipeauctions.bidding.entity.AuctionDisqualification;
import com.swipeauctions.bidding.entity.Bid;
import com.swipeauctions.bidding.repository.AuctionDisqualificationRepository;
import com.swipeauctions.bidding.repository.BidRepository;
import com.swipeauctions.common.exception.BadRequestException;
import com.swipeauctions.common.exception.ResourceNotFoundException;
import com.swipeauctions.notification.AuctionNotificationService;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Core bid placement. One transaction: pessimistic-lock the auction, validate, check the
 * registration-fee-paid and credit-limit gates, insert the bid, update the highest bid, apply
 * anti-snipe, and broadcast to STOMP subscribers only AFTER_COMMIT.
 */
@Service
@RequiredArgsConstructor
public class BidService {

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final AuctionDisqualificationRepository disqualificationRepository;
    private final WalletService walletService;
    private final SimpMessagingTemplate messagingTemplate;
    private final AuctionNotificationService notificationService;

    @Value("${auction.anti-snipe.window-seconds:30}")
    private long antiSnipeWindowSeconds;

    @Value("${auction.anti-snipe.extension-seconds:30}")
    private long antiSnipeExtensionSeconds;

    @Value("${auction.anti-snipe.max-extensions:10}")
    private int maxExtensions;

    @Value("${auction.min-increment:1}")
    private BigDecimal minIncrement;

    /** Per-(auction, bidder) cap — once a bidder has placed this many bids on one item, no more are allowed. */
    public static final int MAX_BIDS_PER_BIDDER_PER_AUCTION = 20;

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
        if (disqualificationRepository.existsByAuction_IdAndBidder_Id(auctionId, bidder.getId())) {
            throw new BadRequestException("You have been disqualified from bidding on this auction.");
        }
        if (!Boolean.TRUE.equals(bidder.getKycCompleted())) {
            throw new BadRequestException("Complete your KYC verification before bidding.");
        }
        if (!Boolean.TRUE.equals(bidder.getRegistrationFeePaid())) {
            throw new BadRequestException("Pay the one-time registration fee to unlock bidding.");
        }

        long bidsPlaced = bidRepository.countByAuction_IdAndBidder_Id(auctionId, bidder.getId());
        if (bidsPlaced >= MAX_BIDS_PER_BIDDER_PER_AUCTION) {
            throw new BadRequestException("No more bids available — you've reached the maximum of "
                    + MAX_BIDS_PER_BIDDER_PER_AUCTION + " bids on this item.");
        }

        // Each bidder only needs to beat their OWN previous bid on this item, not whatever the
        // current leader happens to be — you can keep raising your own bid without needing to know
        // (or clear) the exact figure someone else is at. Whether this particular bid actually takes
        // the lead is a separate question, decided below — "accepted" and "currently winning" aren't
        // the same thing once bids no longer have to leapfrog each other one at a time.
        BigDecimal myPreviousBest = bidRepository.findFirstByAuction_IdAndBidder_IdOrderByAmountDesc(auctionId, bidder.getId())
                .map(Bid::getAmount)
                .orElse(null);
        if (amount.signum() <= 0) {
            throw new BadRequestException("Bid amount must be positive");
        }
        // A bidder's first bid on an item has no floor beyond being positive and within credit limit
        // (checked below) — it doesn't even need to meet the base price; whether the item actually
        // sells at close time is a separate question, decided against the auction's FINAL highest
        // bid (see AuctionService.closeAuction's own basePrice comparison), not any individual bid
        // along the way. Only a SUCCESSIVE bid on this item must clear the bidder's own previous one.
        if (myPreviousBest != null) {
            BigDecimal minAllowed = myPreviousBest.add(minIncrement);
            if (amount.compareTo(minAllowed) < 0) {
                throw new BadRequestException("Bid must be at least " + minAllowed + " — more than your last bid on this item.");
            }
        }

        // Credit limit is a cap on TOTAL exposure across every auction the bidder is currently
        // active on, not a per-bid check in isolation — otherwise a bidder could commit their full
        // limit to any number of auctions at once. committedCredit() sums the bidder's own current
        // bid on each of their other still-open auctions; subtracting this auction's own previous
        // contribution (myPreviousBest) means raising your own bid here only costs the increment,
        // not the full new amount again. A losing auction simply stops counting once it closes (no
        // separate "release" step), and a win is charged for real via the settlement flow below,
        // against the wallet balance itself — this check never moves any actual money.
        //
        // Only the auction row above is locked so far, which isn't enough on its own: this bidder
        // could have a second placeBid() in flight right now against a *different* auction, and
        // that one only locks its own auction row too, so the two committedCredit() reads below
        // could otherwise race and both pass. Locking the wallet row serializes the two calls.
        walletService.lockForBidding(bidder);
        BigDecimal creditLimit = walletService.getCreditLimit(bidder);
        BigDecimal committedElsewhere = walletService.committedCredit(bidder.getId())
                .subtract(myPreviousBest != null ? myPreviousBest : BigDecimal.ZERO);
        BigDecimal availableCredit = creditLimit.subtract(committedElsewhere);
        if (amount.compareTo(availableCredit) > 0) {
            throw new BadRequestException("Bid exceeds your available credit limit of " + availableCredit
                    + " (credit limit " + creditLimit + " minus " + committedElsewhere
                    + " already committed to your other open bids). Top up your wallet to increase it.");
        }

        // Capture who currently leads (the person about to be outbid) before we overwrite the winner.
        User previousWinner = auction.getCurrentWinner();
        String previousWinnerEmail = previousWinner != null ? previousWinner.getEmail() : null;
        boolean previousWasSomeoneElse = previousWinner != null
                && !previousWinner.getId().equals(bidder.getId());
        String auctionTitle = auction.getListing().getTitle();
        String bidderEmail = bidder.getEmail();

        // Counted before inserting (rather than re-querying after) to save a network round trip against
        // the remote DB on every single bid — safe because the auction row is pessimistic-locked above,
        // so nothing else can be inserting a competing bid between this count and our own insert.
        long bidCountAfter = bidRepository.countByAuction_Id(auctionId) + 1;

        Bid bid = bidRepository.save(Bid.builder()
                .auction(auction).bidder(bidder).amount(amount).placedAt(now).build());

        // Accepted doesn't automatically mean winning: only overwrite the auction's leader if this
        // bid is actually higher than the current highest bid (someone else may still be ahead of it).
        boolean becomesNewLeader = auction.getCurrentHighestBid() == null
                || amount.compareTo(auction.getCurrentHighestBid()) > 0;
        if (becomesNewLeader) {
            auction.setCurrentHighestBid(amount);
            auction.setCurrentWinner(bidder);
        }

        // Anti-snipe: a late bid extends the deadline, up to a cap — applies to any accepted bid,
        // not just one that takes the lead, since late activity from a trailing bidder still signals
        // the item is contested.
        if (shouldExtend(now, auction.getCurrentEndTime(), auction.getExtensionCount(),
                antiSnipeWindowSeconds, maxExtensions)) {
            auction.setCurrentEndTime(extend(auction.getCurrentEndTime(), antiSnipeExtensionSeconds));
            auction.setExtensionCount(auction.getExtensionCount() + 1);
        }
        auctionRepository.save(auction);

        // Broadcast the auction's real resulting highest bid, not necessarily this bid's own amount —
        // a trailing bid that didn't take the lead must never make live viewers see the top bid drop.
        broadcastAfterCommit(auctionId, auction.getCurrentHighestBid(), auction.getCurrentEndTime(), bidCountAfter);

        // Notify only once the bid is durably committed (async + non-fatal inside the service).
        String auctionIdStr = auctionId.toString();
        runAfterCommit(() -> {
            notificationService.bidPlaced(bidderEmail, auctionIdStr, auctionTitle, amount, becomesNewLeader);
            if (becomesNewLeader && previousWasSomeoneElse) {
                notificationService.outbid(previousWinnerEmail, auctionIdStr, auctionTitle, amount);
            }
        });
        return bid;
    }

    /**
     * Admin correction tool for a mistaken/disputed bid — overwrites an existing bid's amount
     * directly (bypassing the normal must-beat-your-own-previous-bid / credit-limit checks, which
     * are for the bidder's own self-service flow, not an admin override) and recomputes the
     * auction's leader. Restricted to still-OPEN auctions so this never has to unwind an
     * already-executed settlement.
     */
    @Transactional
    public Bid adminEditBid(UUID bidId, BigDecimal newAmount) {
        Bid bid = bidRepository.findById(bidId)
                .orElseThrow(() -> new ResourceNotFoundException("Bid not found"));
        Auction auction = auctionRepository.findByIdForUpdate(bid.getAuction().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Auction not found"));
        if (auction.getStatus() != AuctionStatus.OPEN) {
            throw new BadRequestException("Cannot modify a bid after the auction has closed.");
        }
        if (newAmount == null || newAmount.signum() <= 0) {
            throw new BadRequestException("Bid amount must be positive");
        }
        bid.setAmount(newAmount);
        bidRepository.save(bid);
        recomputeLeaderAndNotify(auction);
        return bid;
    }

    /** Bans a bidder from one specific auction — releases their EMD hold on it (if any) and
     *  excludes their bids from this auction's ranking going forward; {@link #placeBid} rejects any
     *  further bids from them on it. Their bid history rows are left untouched. */
    @Transactional
    public void disqualifyBidder(UUID auctionId, UUID bidderId, Admin admin, String reason) {
        Auction auction = auctionRepository.findByIdForUpdate(auctionId)
                .orElseThrow(() -> new ResourceNotFoundException("Auction not found"));
        if (auction.getStatus() != AuctionStatus.OPEN) {
            throw new BadRequestException("Can only disqualify a bidder while the auction is open.");
        }
        if (disqualificationRepository.existsByAuction_IdAndBidder_Id(auctionId, bidderId)) {
            throw new BadRequestException("This bidder is already disqualified from this auction.");
        }
        User bidder = bidRepository.findFirstByAuction_IdAndBidder_IdOrderByAmountDesc(auctionId, bidderId)
                .map(Bid::getBidder)
                .orElseThrow(() -> new BadRequestException("This user hasn't bid on this auction."));

        disqualificationRepository.save(AuctionDisqualification.builder()
                .auction(auction).bidder(bidder).admin(admin).reason(reason).build());
        if (walletService.hasActiveHold(auctionId, bidderId)) {
            walletService.releaseHold(auction, bidder);
        }
        recomputeLeaderAndNotify(auction);
    }

    /** Reverses {@link #disqualifyBidder} — their existing bid rows are untouched, so they're
     *  naturally eligible again once the disqualification row is gone. Does not re-place a released
     *  EMD hold; the bidder re-registers normally if they want to bid again. */
    @Transactional
    public void reinstateBidder(UUID auctionId, UUID bidderId) {
        Auction auction = auctionRepository.findByIdForUpdate(auctionId)
                .orElseThrow(() -> new ResourceNotFoundException("Auction not found"));
        AuctionDisqualification dq = disqualificationRepository.findByAuction_IdAndBidder_Id(auctionId, bidderId)
                .orElseThrow(() -> new ResourceNotFoundException("This bidder isn't disqualified from this auction."));
        disqualificationRepository.delete(dq);
        recomputeLeaderAndNotify(auction);
    }

    /** Shared by the three admin methods above: recomputes the auction's leader from its currently
     *  eligible bids, broadcasts the result over the same STOMP topic {@link #placeBid} uses, and —
     *  if someone lost the lead as a result — notifies them, same after-commit deferral as placeBid.
     *  Caller must already hold the auction's row lock. */
    private void recomputeLeaderAndNotify(Auction auction) {
        User previousWinner = auction.getCurrentWinner();

        List<Bid> eligible = bidRepository.findEligibleByAuction_IdOrderByAmountDesc(auction.getId());
        Bid newLeadBid = eligible.isEmpty() ? null : eligible.get(0);
        auction.setCurrentHighestBid(newLeadBid != null ? newLeadBid.getAmount() : null);
        auction.setCurrentWinner(newLeadBid != null ? newLeadBid.getBidder() : null);
        auctionRepository.save(auction);

        UUID auctionId = auction.getId();
        String auctionTitle = auction.getListing().getTitle();
        BigDecimal newHighest = auction.getCurrentHighestBid();
        LocalDateTime endTime = auction.getCurrentEndTime();
        long bidCount = bidRepository.countByAuction_Id(auctionId);
        User newWinner = auction.getCurrentWinner();

        boolean leaderChanged = !Objects.equals(
                previousWinner != null ? previousWinner.getId() : null,
                newWinner != null ? newWinner.getId() : null);
        String previousWinnerEmail = previousWinner != null ? previousWinner.getEmail() : null;

        broadcastAfterCommit(auctionId, newHighest, endTime, bidCount);
        if (leaderChanged && previousWinnerEmail != null) {
            runAfterCommit(() -> notificationService.outbid(previousWinnerEmail, auctionId.toString(), auctionTitle, newHighest));
        }
    }

    /** Pure anti-snipe decision: does a bid landing this close to the deadline warrant an extension? */
    static boolean shouldExtend(LocalDateTime now, LocalDateTime currentEndTime, int extensionCount,
                                 long windowSeconds, int maxExtensions) {
        long remaining = Duration.between(now, currentEndTime).getSeconds();
        return remaining <= windowSeconds && extensionCount < maxExtensions;
    }

    /** Pure anti-snipe extension: push the deadline out by {@code extensionSeconds}. */
    static LocalDateTime extend(LocalDateTime currentEndTime, long extensionSeconds) {
        return currentEndTime.plusSeconds(extensionSeconds);
    }

    /** Run {@code action} after the current transaction commits, or immediately if none is active. */
    private void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
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
