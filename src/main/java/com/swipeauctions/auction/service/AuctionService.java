package com.swipeauctions.auction.service;

import com.swipeauctions.auction.entity.Auction;
import com.swipeauctions.auction.enums.AuctionStatus;
import com.swipeauctions.auction.repository.AuctionRepository;
import com.swipeauctions.catalog.entity.Listing;
import com.swipeauctions.catalog.enums.ListingStatus;
import com.swipeauctions.catalog.repository.ListingRepository;
import com.swipeauctions.common.exception.BadRequestException;
import com.swipeauctions.common.exception.ResourceNotFoundException;
import com.swipeauctions.user.entity.User;
import com.swipeauctions.wallet.entity.BidEligibilityHold;
import com.swipeauctions.wallet.enums.HoldStatus;
import com.swipeauctions.wallet.repository.BidEligibilityHoldRepository;
import com.swipeauctions.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Auction lifecycle: create from a listing, open at start time, close at (extended) end time. */
@Service
@RequiredArgsConstructor
public class AuctionService {

    private final AuctionRepository auctionRepository;
    private final ListingRepository listingRepository;
    private final BidEligibilityHoldRepository holdRepository;
    private final WalletService walletService;

    @Transactional
    public Auction createAuction(User seller, UUID listingId, BigDecimal basePrice,
                                 LocalDateTime startTime, LocalDateTime endTime) {
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

        listing.setStatus(ListingStatus.PUBLISHED);
        listingRepository.save(listing);

        return auctionRepository.save(Auction.builder()
                .listing(listing)
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

    @Transactional(readOnly = true)
    public List<Auction> list(AuctionStatus status) {
        return status != null ? auctionRepository.findByStatus(status) : auctionRepository.findAll();
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

    /** Scheduler hook: close OPEN auctions past their (possibly extended) end time and settle holds. */
    @Transactional
    public void closeDueAuctions() {
        LocalDateTime now = LocalDateTime.now();
        for (Auction a : auctionRepository.findByStatusAndCurrentEndTimeLessThanEqual(AuctionStatus.OPEN, now)) {
            closeAuction(a);
        }
    }

    @Transactional
    public void closeAuction(Auction a) {
        boolean sold = a.getCurrentWinner() != null
                && a.getCurrentHighestBid() != null
                && a.getCurrentHighestBid().compareTo(a.getBasePrice()) >= 0;

        List<BidEligibilityHold> holds = holdRepository.findByAuction_IdAndStatus(a.getId(), HoldStatus.ACTIVE);
        if (sold) {
            a.setStatus(AuctionStatus.CLOSED);
            for (BidEligibilityHold hold : holds) {
                if (hold.getBidder().getId().equals(a.getCurrentWinner().getId())) {
                    walletService.captureHold(a, hold.getBidder());
                } else {
                    walletService.releaseHold(a, hold.getBidder());
                }
            }
        } else {
            a.setStatus(AuctionStatus.RESERVE_NOT_MET);
            for (BidEligibilityHold hold : holds) {
                walletService.releaseHold(a, hold.getBidder());
            }
        }
        auctionRepository.save(a);
    }
}
