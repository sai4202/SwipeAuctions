package com.swipeauctions.wallet.repository;

import com.swipeauctions.wallet.entity.BidEligibilityHold;
import com.swipeauctions.wallet.enums.HoldStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BidEligibilityHoldRepository extends JpaRepository<BidEligibilityHold, UUID> {

    Optional<BidEligibilityHold> findByAuction_IdAndBidder_Id(UUID auctionId, UUID bidderId);

    /**
     * Pessimistic row lock for release/capture — the hold row itself must be locked before its
     * ACTIVE status is checked, otherwise two concurrent callers (admin double-click, or the
     * auto-release scheduler racing a manual admin release) can both read ACTIVE and both apply
     * their credit, double-releasing/capturing the same EMD. See Findings_pendings.md #3.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from BidEligibilityHold h where h.auction.id = :auctionId and h.bidder.id = :bidderId")
    Optional<BidEligibilityHold> findByAuction_IdAndBidder_IdForUpdate(
            @Param("auctionId") UUID auctionId, @Param("bidderId") UUID bidderId);

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
