package com.swipeauctions.bidding.repository;

import com.swipeauctions.bidding.entity.Bid;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    interface AuctionBidCount {
        UUID getAuctionId();
        long getCnt();
    }
}
