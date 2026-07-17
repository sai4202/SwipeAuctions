package com.swipeauctions.bid.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.swipeauctions.bid.entity.Bid;

import java.util.UUID;

@Repository
public interface BidRepository extends JpaRepository<Bid, UUID> {

}