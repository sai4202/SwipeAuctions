package com.swipeauctions.bidding.repository;

import com.swipeauctions.bidding.entity.Bid;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BidRepository extends JpaRepository<Bid, UUID> {

    List<Bid> findByAuction_IdOrderByAmountDesc(UUID auctionId);

    long countByAuction_Id(UUID auctionId);

    long countByAuction_IdAndBidder_Id(UUID auctionId, UUID bidderId);

    /** The current user's highest bid on an auction, if any. */
    Optional<Bid> findFirstByAuction_IdAndBidder_IdOrderByAmountDesc(UUID auctionId, UUID bidderId);

    /** Batch bid-count-per-auction for the browse list — one round trip instead of one per auction. */
    @Query("select b.auction.id as auctionId, count(b) as cnt from Bid b where b.auction.id in :auctionIds group by b.auction.id")
    List<AuctionBidCount> countByAuction_IdIn(@Param("auctionIds") List<UUID> auctionIds);

    /** All of one bidder's bids across a set of auctions — grouped/maxed in-memory to get "your bid" per auction. */
    List<Bid> findByAuction_IdInAndBidder_Id(List<UUID> auctionIds, UUID bidderId);

    /** Every bid a user has ever placed, across every auction — the admin "bidding activity" view
     *  groups/maxes these in-memory per auction the same way findByAuction_IdInAndBidder_Id's callers do. */
    List<Bid> findByBidder_Id(UUID bidderId);

    /** How many auctions this user is *currently* bidding on (item still OPEN) — the admin user list's
     *  "Active Bids" column. Distinct because a bidder can have placed several bids on one auction. */
    @Query("select count(distinct b.auction.id) from Bid b where b.bidder.id = :bidderId and b.auction.status = com.swipeauctions.auction.enums.AuctionStatus.OPEN")
    long countDistinctOpenAuctionsByBidder(@Param("bidderId") UUID bidderId);

    /** This bidder's own current (max) bid on each of their still-OPEN auctions — what's presently
     *  "committed" out of their credit limit while those auctions remain live (see
     *  WalletService.committedCredit / BidService.placeBid's credit-limit check). An auction drops
     *  out the moment it closes, which is what makes a lost auction's exposure free itself
     *  automatically with no separate "release" step needed. */
    @Query("select max(b.amount) from Bid b where b.bidder.id = :bidderId and b.auction.status = com.swipeauctions.auction.enums.AuctionStatus.OPEN group by b.auction.id")
    List<BigDecimal> findMaxBidPerOpenAuctionForBidder(@Param("bidderId") UUID bidderId);

    interface AuctionBidCount {
        UUID getAuctionId();
        long getCnt();
    }
}
