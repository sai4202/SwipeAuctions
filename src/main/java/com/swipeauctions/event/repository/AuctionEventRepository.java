package com.swipeauctions.event.repository;

import com.swipeauctions.event.entity.AuctionEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuctionEventRepository extends JpaRepository<AuctionEvent, UUID> {

    List<AuctionEvent> findBySeller_IdOrderByStartTimeDesc(UUID sellerId);

    List<AuctionEvent> findByCategory_SlugOrderByStartTimeDesc(String categorySlug);

    List<AuctionEvent> findAllByOrderByStartTimeDesc();
}
