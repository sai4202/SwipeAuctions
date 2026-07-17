package com.swipeauctions.bidding.repository;

import com.swipeauctions.bidding.entity.Bid;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BidRepository extends JpaRepository<Bid, UUID> {

    List<Bid> findByAuction_IdOrderByAmountDesc(UUID auctionId);

    long countByAuction_Id(UUID auctionId);
}
