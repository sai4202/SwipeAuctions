package com.swipeauctions.wallet.repository;

import com.swipeauctions.wallet.entity.BidEligibilityHold;
import com.swipeauctions.wallet.enums.HoldStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BidEligibilityHoldRepository extends JpaRepository<BidEligibilityHold, UUID> {

    Optional<BidEligibilityHold> findByAuction_IdAndBidder_Id(UUID auctionId, UUID bidderId);

    boolean existsByAuction_IdAndBidder_IdAndStatus(UUID auctionId, UUID bidderId, HoldStatus status);

    List<BidEligibilityHold> findByAuction_IdAndStatus(UUID auctionId, HoldStatus status);
}
