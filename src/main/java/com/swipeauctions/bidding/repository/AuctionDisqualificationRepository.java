package com.swipeauctions.bidding.repository;

import com.swipeauctions.bidding.entity.AuctionDisqualification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuctionDisqualificationRepository extends JpaRepository<AuctionDisqualification, UUID> {

    boolean existsByAuction_IdAndBidder_Id(UUID auctionId, UUID bidderId);

    Optional<AuctionDisqualification> findByAuction_IdAndBidder_Id(UUID auctionId, UUID bidderId);

    /** Used by the admin bidders list to mark which rows are disqualified. */
    List<AuctionDisqualification> findByAuction_Id(UUID auctionId);
}
