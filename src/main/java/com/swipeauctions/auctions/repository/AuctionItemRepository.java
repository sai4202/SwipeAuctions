package com.swipeauctions.auctions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.swipeauctions.auctions.entity.AuctionItem;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuctionItemRepository extends JpaRepository<AuctionItem, UUID> {

    Optional<AuctionItem> findByLotNumber(String lotNumber);

    boolean existsByLotNumber(String lotNumber);

}