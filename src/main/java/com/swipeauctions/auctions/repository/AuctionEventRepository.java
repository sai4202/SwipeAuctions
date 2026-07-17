package com.swipeauctions.auctions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.swipeauctions.auctions.entity.AuctionEvent;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuctionEventRepository extends JpaRepository<AuctionEvent, UUID> {

    Optional<AuctionEvent> findByEventCode(String eventCode);

    boolean existsByEventCode(String eventCode);

}