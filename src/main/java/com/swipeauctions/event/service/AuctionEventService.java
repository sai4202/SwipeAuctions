package com.swipeauctions.event.service;

import com.swipeauctions.catalog.entity.Category;
import com.swipeauctions.common.exception.ResourceNotFoundException;
import com.swipeauctions.event.entity.AuctionEvent;
import com.swipeauctions.event.repository.AuctionEventRepository;
import com.swipeauctions.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Seller-created auction events, each scoped to one category (e.g. Bank Vehicles, Insurance). */
@Service
@RequiredArgsConstructor
public class AuctionEventService {

    private final AuctionEventRepository auctionEventRepository;

    @Transactional
    public AuctionEvent createEvent(User seller, Category category, String name, String location,
                                    LocalDateTime startTime, LocalDateTime closingTime) {
        return auctionEventRepository.save(AuctionEvent.builder()
                .seller(seller)
                .category(category)
                .name(name)
                .location(location)
                .startTime(startTime)
                .closingTime(closingTime)
                .build());
    }

    @Transactional(readOnly = true)
    public List<AuctionEvent> listEvents(String categorySlug) {
        return categorySlug == null || categorySlug.isBlank()
                ? auctionEventRepository.findAllByOrderByStartTimeDesc()
                : auctionEventRepository.findByCategory_SlugOrderByStartTimeDesc(categorySlug);
    }

    @Transactional(readOnly = true)
    public List<AuctionEvent> listMyEvents(User seller) {
        return auctionEventRepository.findBySeller_IdOrderByStartTimeDesc(seller.getId());
    }

    @Transactional(readOnly = true)
    public AuctionEvent getEvent(UUID id) {
        return auctionEventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Auction event not found"));
    }
}
