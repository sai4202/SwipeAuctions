package com.swipeauctions.wallet.repository;

import com.swipeauctions.wallet.entity.BidEligibilityHold;
import com.swipeauctions.wallet.enums.HoldStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BidEligibilityHoldRepository extends JpaRepository<BidEligibilityHold, UUID> {

    Optional<BidEligibilityHold> findByAuction_IdAndBidder_Id(UUID auctionId, UUID bidderId);

    boolean existsByAuction_IdAndBidder_IdAndStatus(UUID auctionId, UUID bidderId, HoldStatus status);

    boolean existsByAuction_IdAndStatus(UUID auctionId, HoldStatus status);

    List<BidEligibilityHold> findByAuction_IdAndStatus(UUID auctionId, HoldStatus status);

    List<BidEligibilityHold> findByBidder_IdAndStatus(UUID bidderId, HoldStatus status);

    /**
     * Batch "does this bidder have an active hold" check for the browse list — one round trip for
     * every auction instead of one per auction; caller collects the returned auction ids into a set.
     */
    @Query("select h.auction.id from BidEligibilityHold h "
            + "where h.bidder.id = :bidderId and h.auction.id in :auctionIds and h.status = :status")
    List<UUID> findAuctionIdsWithActiveHold(
            @Param("bidderId") UUID bidderId, @Param("auctionIds") List<UUID> auctionIds, @Param("status") HoldStatus status);
}
