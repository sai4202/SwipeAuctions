package com.swipeauctions.auction.service;

import com.swipeauctions.auction.entity.Auction;
import com.swipeauctions.auction.enums.AuctionStatus;
import com.swipeauctions.auction.repository.AuctionRepository;
import com.swipeauctions.bidding.repository.BidRepository;
import com.swipeauctions.catalog.entity.Listing;
import com.swipeauctions.catalog.enums.ListingStatus;
import com.swipeauctions.catalog.repository.ListingRepository;
import com.swipeauctions.common.exception.BadRequestException;
import com.swipeauctions.common.exception.ResourceNotFoundException;
import com.swipeauctions.dispute.enums.DisputeStatus;
import com.swipeauctions.dispute.repository.DisputeRepository;
import com.swipeauctions.event.entity.AuctionEvent;
import com.swipeauctions.event.repository.AuctionEventRepository;
import com.swipeauctions.notification.AuctionNotificationService;
import com.swipeauctions.user.entity.User;
import com.swipeauctions.wallet.entity.BidEligibilityHold;
import com.swipeauctions.wallet.entity.SaleProceedsHold;
import com.swipeauctions.wallet.enums.HoldStatus;
import com.swipeauctions.wallet.repository.BidEligibilityHoldRepository;
import com.swipeauctions.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Auction lifecycle: create from a listing, open at start time, close at (extended) end time. */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuctionService {

    private static final List<DisputeStatus> OPEN_DISPUTE_STATUSES = List.of(DisputeStatus.OPEN, DisputeStatus.IN_REVIEW);

    /** Categories that support seller-created auction events (grouped sales), same UI for each. */
    private static final Set<String> EVENT_CATEGORY_SLUGS = Set.of("bank-vehicles", "insurance", "premium", "auto");

    private final AuctionRepository auctionRepository;
    private final ListingRepository listingRepository;
    private final BidRepository bidRepository;
    private final BidEligibilityHoldRepository holdRepository;
    private final WalletService walletService;
    private final AuctionNotificationService notificationService;
    private final DisputeRepository disputeRepository;
    private final AuctionEventRepository auctionEventRepository;

    /** How long seller proceeds stay escrowed before auto-release, absent an open dispute. */
    @Value("${dispute.proceeds-hold-hours:48}")
    private long proceedsHoldHours;

    @Transactional
    public Auction createAuction(User seller, UUID listingId, BigDecimal basePrice,
                                 LocalDateTime startTime, LocalDateTime endTime, UUID eventId) {
        Listing listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new ResourceNotFoundException("Listing not found"));
        if (!listing.getSeller().getId().equals(seller.getId())) {
            throw new BadRequestException("You can only auction your own listings");
        }
        if (auctionRepository.findByListing_Id(listingId).isPresent()) {
            throw new BadRequestException("An auction already exists for this listing");
        }
        if (endTime.isBefore(startTime)) {
            throw new BadRequestException("End time must be after start time");
        }
        BigDecimal base = basePrice != null ? basePrice : listing.getReservePrice();
        AuctionStatus status = startTime.isAfter(LocalDateTime.now())
                ? AuctionStatus.SCHEDULED : AuctionStatus.OPEN;

        AuctionEvent event = null;
        if (eventId != null) {
            event = auctionEventRepository.findById(eventId)
                    .orElseThrow(() -> new ResourceNotFoundException("Auction event not found"));
            if (!event.getSeller().getId().equals(seller.getId())) {
                throw new BadRequestException("You can only add auctions to your own events");
            }
            String listingCategorySlug = listing.getCategory().getSlug();
            if (!EVENT_CATEGORY_SLUGS.contains(listingCategorySlug)) {
                throw new BadRequestException("Auction events are only supported for the Bank Vehicles, Insurance, Premium and Auto categories");
            }
            if (!event.getCategory().getSlug().equals(listingCategorySlug)) {
                throw new BadRequestException("This item's category doesn't match the auction event's category");
            }
        }

        listing.setStatus(ListingStatus.PUBLISHED);
        listingRepository.save(listing);

        return auctionRepository.save(Auction.builder()
                .listing(listing)
                .event(event)
                .basePrice(base)
                .startTime(startTime)
                .endTime(endTime)
                .currentEndTime(endTime)
                .status(status)
                .build());
    }

    @Transactional(readOnly = true)
    public Auction get(UUID auctionId) {
        return auctionRepository.findById(auctionId)
                .orElseThrow(() -> new ResourceNotFoundException("Auction not found"));
    }

    /**
     * Same as {@link #get} but takes the pessimistic row lock {@code BidService.placeBid} uses —
     * for the close/settlement paths below, so a concurrent close (scheduler tick vs. admin
     * force-close vs. a double-submitted "complete payment") serializes instead of relying solely on
     * optimistic-lock rollback as the only safety net.
     */
    private Auction getForUpdate(UUID auctionId) {
        return auctionRepository.findByIdForUpdate(auctionId)
                .orElseThrow(() -> new ResourceNotFoundException("Auction not found"));
    }

    @Transactional(readOnly = true)
    public List<Auction> list(AuctionStatus status) {
        return status != null ? auctionRepository.findByStatus(status) : auctionRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Auction> listWonBy(UUID winnerId) {
        return auctionRepository.findByCurrentWinner_IdAndStatusOrderByCurrentEndTimeDesc(winnerId, AuctionStatus.CLOSED);
    }

    /** Scheduler hook: flip SCHEDULED → OPEN once start time has passed. */
    @Transactional
    public void openDueAuctions() {
        LocalDateTime now = LocalDateTime.now();
        for (Auction a : auctionRepository.findByStatusAndStartTimeLessThanEqual(AuctionStatus.SCHEDULED, now)) {
            a.setStatus(AuctionStatus.OPEN);
            auctionRepository.save(a);
        }
    }

    /**
     * Scheduler hook: IDs of auctions past their (possibly extended) end time, still OPEN. IDs only
     * (not the entities) so the caller re-fetches and closes each one in its own transaction — closing
     * them all under one shared transaction would mean one bad auction (e.g. a settlement failure)
     * rolls back every other auction's close in the same tick.
     */
    @Transactional(readOnly = true)
    public List<UUID> findAuctionsDueForClose() {
        return auctionRepository.findByStatusAndCurrentEndTimeLessThanEqual(AuctionStatus.OPEN, LocalDateTime.now())
                .stream().map(Auction::getId).toList();
    }

    /** Scheduler hook: re-fetches the auction fresh (own transaction/session) and closes it. */
    @Transactional
    public void closeAuctionById(UUID auctionId) {
        closeAuction(getForUpdate(auctionId));
    }

    /** Admin action: close an OPEN auction before its scheduled end time, settling exactly as closeAuction does. */
    @Transactional
    public Auction forceClose(UUID auctionId) {
        Auction a = getForUpdate(auctionId);
        if (a.getStatus() != AuctionStatus.OPEN) {
            throw new BadRequestException("Only an OPEN auction can be force-closed");
        }
        closeAuction(a);
        return a;
    }

    /**
     * Admin action: edit an auction's title/base price/schedule. Blocked once a bid has been placed —
     * {@code basePrice} is also the wallet-hold amount every bidder's deposit was computed against, so
     * changing it (or the base price a placed bid must beat) after the fact would desync existing holds
     * from the number they were sized for. CLOSED/UNSOLD auctions are settled and not editable.
     */
    @Transactional
    public Auction adminUpdate(UUID auctionId, String title, BigDecimal basePrice,
                                LocalDateTime startTime, LocalDateTime endTime) {
        // Locks the auction row (matching forceClose/closeAuction) so this genuinely serializes
        // against WalletService.placeHold, which locks the same row before sizing a new EMD hold
        // to basePrice — otherwise a hold could commit in the window between this method's read
        // and its save, desyncing the hold amount from the price it's supposed to be locked to.
        // See Findings_pendings.md #4.
        Auction a = getForUpdate(auctionId);
        if (a.getStatus() != AuctionStatus.SCHEDULED && a.getStatus() != AuctionStatus.OPEN) {
            throw new BadRequestException("Only a scheduled or open auction can be modified");
        }
        if (bidRepository.countByAuction_Id(auctionId) > 0) {
            throw new BadRequestException("Cannot modify an auction that already has bids");
        }
        // A bidder can register (and lock an EMD hold sized against the *current* basePrice) even
        // before any bid exists, so the bid-count check above isn't enough on its own — changing
        // basePrice after that would desync settlementRemainder()/creditSaleProceeds() from what was
        // actually held.
        if (basePrice.compareTo(a.getBasePrice()) != 0
                && holdRepository.existsByAuction_IdAndStatus(auctionId, HoldStatus.ACTIVE)) {
            throw new BadRequestException(
                    "Cannot change the base price — a bidder has already registered with an EMD hold locked in at the current price");
        }
        if (endTime.isBefore(startTime)) {
            throw new BadRequestException("End time must be after start time");
        }

        Listing listing = a.getListing();
        listing.setTitle(title);
        listingRepository.save(listing);

        a.setBasePrice(basePrice);
        a.setStartTime(startTime);
        a.setEndTime(endTime);
        a.setCurrentEndTime(endTime);
        a.setStatus(startTime.isAfter(LocalDateTime.now()) ? AuctionStatus.SCHEDULED : AuctionStatus.OPEN);
        return auctionRepository.save(a);
    }

    @Transactional
    public void closeAuction(Auction a) {
        boolean sold = a.getCurrentWinner() != null
                && a.getCurrentHighestBid() != null
                && a.getCurrentHighestBid().compareTo(a.getBasePrice()) >= 0;

        String title = a.getListing().getTitle();
        String auctionIdStr = a.getId().toString();
        List<BidEligibilityHold> holds = holdRepository.findByAuction_IdAndStatus(a.getId(), HoldStatus.ACTIVE);
        if (sold) {
            a.setStatus(AuctionStatus.CLOSED);
            for (BidEligibilityHold hold : holds) {
                User b = hold.getBidder();
                if (b.getId().equals(a.getCurrentWinner().getId())) {
                    walletService.captureHold(a, b);
                    notificationService.auctionWon(b.getEmail(), auctionIdStr, title, a.getCurrentHighestBid());
                } else {
                    BigDecimal newCreditLimit = walletService.releaseHold(a, b);
                    notificationService.auctionEndedNotWon(b.getEmail(), auctionIdStr, title, true, newCreditLimit);
                }
            }
            // Settle the remainder immediately if funds allow (internal wallet debit, no Razorpay call
            // — same rule as hold/release/capture). If the winner (dealer or otherwise) doesn't have
            // enough available balance yet, leave settlementPaid false so they can pay it off later.
            User winner = a.getCurrentWinner();
            a.setSettlementPaid(walletService.captureRemainder(a, winner, settlementRemainder(a, winner)));
            if (a.isSettlementPaid()) {
                walletService.creditSaleProceeds(a, a.getListing().getSeller(), a.getCurrentHighestBid());
            }
        } else {
            a.setStatus(AuctionStatus.UNSOLD);
            for (BidEligibilityHold hold : holds) {
                User b = hold.getBidder();
                BigDecimal newCreditLimit = walletService.releaseHold(a, b);
                notificationService.auctionEndedNotWon(b.getEmail(), auctionIdStr, title, false, newCreditLimit);
            }
        }
        auctionRepository.save(a);
    }

    /**
     * Winner pays off the settlement remainder later (e.g. after topping up their wallet), if it
     * couldn't be captured in full at close time. Idempotent — no-ops once already settled.
     */
    @Transactional
    public Auction completeSettlement(UUID auctionId, User winner) {
        Auction a = getForUpdate(auctionId);
        if (a.getStatus() != AuctionStatus.CLOSED) {
            throw new BadRequestException("This auction isn't closed yet");
        }
        if (a.getCurrentWinner() == null || !a.getCurrentWinner().getId().equals(winner.getId())) {
            throw new BadRequestException("Only the winning bidder can complete this payment");
        }
        if (a.isSettlementPaid()) {
            return a;
        }
        BigDecimal remainder = settlementRemainder(a, winner);
        boolean paid = walletService.captureRemainder(a, winner, remainder);
        if (!paid) {
            throw new BadRequestException("Insufficient wallet balance. Top up at least " + remainder + " to complete this payment.");
        }
        a.setSettlementPaid(true);
        walletService.creditSaleProceeds(a, a.getListing().getSeller(), a.getCurrentHighestBid());
        return auctionRepository.save(a);
    }

    /**
     * Scheduler hook: auto-releases escrowed seller proceeds past {@code dispute.proceeds-hold-hours}
     * that don't have an open dispute against their auction. Disputes raised within the window keep
     * the hold ACTIVE indefinitely until an admin resolves it (release or refund).
     */
    public void releaseDueProceeds() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(proceedsHoldHours);
        for (SaleProceedsHold hold : walletService.findProceedsDueForRelease(threshold)) {
            if (!disputeRepository.existsByAuction_IdAndStatusIn(hold.getAuction().getId(), OPEN_DISPUTE_STATUSES)) {
                // Each release runs in its own transaction (releaseSaleProceeds is @Transactional on
                // WalletService, a separate bean) so one bad hold can't roll back every other seller's
                // payout in the same tick.
                try {
                    walletService.releaseSaleProceeds(hold);
                } catch (Exception e) {
                    log.warn("Failed to release sale proceeds for hold {} (auction {}): {}",
                            hold.getId(), hold.getAuction().getId(), e.getMessage());
                }
            }
        }
    }

    /**
     * What the winner still owes beyond what's already been captured: {@code currentHighestBid}
     * minus the EMD (base price) — but the full {@code currentHighestBid} for a dealer, who is
     * EMD-exempt and so never had a hold captured for this auction in the first place.
     */
    private BigDecimal settlementRemainder(Auction a, User winner) {
        boolean hadEmdCaptured = holdRepository.findByAuction_IdAndBidder_Id(a.getId(), winner.getId())
                .map(h -> h.getStatus() == HoldStatus.CAPTURED)
                .orElse(false);
        return hadEmdCaptured ? a.getCurrentHighestBid().subtract(a.getBasePrice()) : a.getCurrentHighestBid();
    }
}
